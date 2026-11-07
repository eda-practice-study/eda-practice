package com.eda.stock.service;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.message.stock.StockCreated;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.stock.domain.ProcessedMessage;
import com.eda.stock.domain.ProcessedMessageRepository;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@DirtiesContext
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=stock",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class StockServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private StockService stockService;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ProcessedMessageRepository processedMessageRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        processedMessageRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("새 상품이면 재고와 처리 이력 및 생성 이벤트를 함께 저장한다")
    void createFor_shouldPersistEvent_whenProductNew() {
        UUID messageId = UUID.randomUUID();

        boolean created = stockService.createFor(messageId, 1L);

        assertThat(created).isTrue();
        Stock stock = stockRepository.findByProductId(1L).orElseThrow();
        assertThat(stock.getQuantity()).isZero();
        ProcessedMessage history = processedMessageRepository.findById(messageId).orElseThrow();
        assertThat(history.getProcessedAt()).isNotNull();
        assertThat(history.isNew()).isFalse();
        List<OutboxMessage> messages = outboxRepository.findAll();
        assertThat(messages).hasSize(1);
        OutboxMessage message = messages.getFirst();
        assertThat(message.getTopic()).isEqualTo("stock.events");
        assertThat(message.getMessageKey()).isEqualTo("1");
        assertThat(message.getMessageType()).isEqualTo("StockCreated");
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getMessageId()).isNotNull().isNotEqualTo(messageId);
        assertThat(jsonMapper.readValue(message.getPayload(), StockCreated.class)).isEqualTo(new StockCreated(1L, 0));
    }

    @Test
    @DisplayName("같은 메시지를 다시 받으면 기존 재고 수량과 생성 이벤트를 유지한다")
    void createFor_shouldSkip_whenMessageAlreadyProcessed() {
        UUID messageId = UUID.randomUUID();
        stockService.createFor(messageId, 1L);
        Stock stock = stockRepository.findByProductId(1L).orElseThrow();
        stock.increase(10);
        stockRepository.saveAndFlush(stock);

        boolean created = stockService.createFor(messageId, 1L);

        assertThat(created).isFalse();
        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(processedMessageRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 메시지로 기존 상품을 받으면 재고 수량을 보존하고 새 처리 이력만 저장한다")
    void createFor_shouldPreserveStock_whenProductAlreadyExists() {
        stockService.createFor(UUID.randomUUID(), 1L);
        Stock stock = stockRepository.findByProductId(1L).orElseThrow();
        stock.increase(10);
        stockRepository.saveAndFlush(stock);
        UUID messageId = UUID.randomUUID();

        boolean created = stockService.createFor(messageId, 1L);

        assertThat(created).isFalse();
        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(processedMessageRepository.existsById(messageId)).isTrue();
        assertThat(processedMessageRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("재고 INSERT가 실패하면 먼저 저장한 처리 이력도 롤백하고 재시도할 수 있다")
    void createFor_shouldRecover_whenStockSaveRestored() {
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.execute("alter table stock.stock add constraint test_reject_stock check (product_id <> 2)");
        try {
            assertThatThrownBy(() -> stockService.createFor(messageId, 2L))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(processedMessageRepository.existsById(messageId)).isFalse();
            assertThat(stockRepository.existsByProductId(2L)).isFalse();
            assertThat(outboxRepository.count()).isZero();
        } finally {
            jdbcTemplate.execute("alter table stock.stock drop constraint test_reject_stock");
        }

        stockService.createFor(messageId, 2L);

        assertThat(processedMessageRepository.existsById(messageId)).isTrue();
        assertThat(stockRepository.findByProductId(2L).orElseThrow().getQuantity()).isZero();
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Outbox INSERT가 실패하면 재고와 처리 이력도 롤백하고 재시도할 수 있다")
    void createFor_shouldRecover_whenOutboxSaveRestored() {
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.execute("alter table stock.outbox_message add constraint test_reject_event check (topic <> 'stock.events')");
        try {
            assertThatThrownBy(() -> stockService.createFor(messageId, 4L))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(processedMessageRepository.existsById(messageId)).isFalse();
            assertThat(stockRepository.existsByProductId(4L)).isFalse();
            assertThat(outboxRepository.count()).isZero();
        } finally {
            jdbcTemplate.execute("alter table stock.outbox_message drop constraint test_reject_event");
        }

        assertThat(stockService.createFor(messageId, 4L)).isTrue();

        assertThat(processedMessageRepository.existsById(messageId)).isTrue();
        assertThat(stockRepository.findByProductId(4L).orElseThrow().getQuantity()).isZero();
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 메시지를 동시에 받으면 한 건의 생성 이벤트만 저장한다")
    void createFor_shouldCreateOnce_whenSameMessageConcurrent() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = first;
        var barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = java.util.stream.Stream.of(first, second).map(id -> executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    stockService.createFor(id, 3L);
                } catch (DataIntegrityViolationException ex) {
                    // 리스너의 재시도처럼 새 트랜잭션에서 다시 처리한다.
                    stockService.createFor(id, 3L);
                }
                return null;
            })).toList();
            for (Future<Object> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(stockRepository.findByProductId(3L).orElseThrow().getQuantity()).isZero();
        assertThat(processedMessageRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 메시지로 같은 상품을 동시에 받아도 한 건의 생성 이벤트만 저장한다")
    void createFor_shouldCreateOnce_whenSameProductConcurrent() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = java.util.stream.Stream.of(first, second).map(id -> executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    stockService.createFor(id, 3L);
                } catch (DataIntegrityViolationException ex) {
                    // 리스너의 재시도처럼 새 트랜잭션에서 다시 처리한다.
                    stockService.createFor(id, 3L);
                }
                return null;
            })).toList();
            for (Future<Object> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(stockRepository.findByProductId(3L).orElseThrow().getQuantity()).isZero();
        assertThat(processedMessageRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }
}
