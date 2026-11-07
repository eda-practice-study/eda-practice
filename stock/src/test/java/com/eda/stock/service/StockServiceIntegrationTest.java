package com.eda.stock.service;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.message.stock.StockCreated;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.stock.domain.ProcessedMessageRepository;
import com.eda.stock.domain.StockRepository;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
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
    @DisplayName("재고와 처리 이력 및 이벤트를 함께 커밋하고 재수신 시 이벤트를 추가하지 않는다")
    void createAndRedeliver() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        stockService.createFor(first, 1L);
        var stock = stockRepository.findByProductId(1L).orElseThrow();
        assertThat(stock.getQuantity()).isZero();
        var history = processedMessageRepository.findById(first).orElseThrow();
        assertThat(history.getProcessedAt()).isNotNull();
        assertThat(history.isNew()).isFalse();
        stock.increase(10);
        stockRepository.saveAndFlush(stock);

        stockService.createFor(first, 1L);
        stockService.createFor(second, 1L);

        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(stockRepository.findByProductId(1L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(processedMessageRepository.count()).isEqualTo(2);
        var outbox = outboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        assertThat(outbox.getFirst().getTopic()).isEqualTo("stock.events");
        assertThat(outbox.getFirst().getMessageKey()).isEqualTo("1");
        assertThat(outbox.getFirst().getMessageType()).isEqualTo("StockCreated");
        assertThat(outbox.getFirst().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getFirst().getMessageId()).isNotNull().isNotEqualTo(first);
        assertThat(jsonMapper.readValue(outbox.getFirst().getPayload(), StockCreated.class))
                .isEqualTo(new StockCreated(1L, 0));
    }

    @Test
    @DisplayName("재고 INSERT가 실패하면 먼저 저장한 처리 이력도 롤백하고 재시도할 수 있다")
    void rollbackAndRetry() {
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
    void outboxFailureRollsBackAndRetries() {
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("같은 상품을 동시에 처리해도 유일 제약과 재시도로 재고 한 건만 생성한다")
    void concurrentDelivery(boolean sameMessageId) throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = sameMessageId ? first : UUID.randomUUID();
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.Stream.of(first, second).map(id -> executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    stockService.createFor(id, 3L);
                } catch (DataIntegrityViolationException ex) {
                    // 리스너의 재시도처럼 새 트랜잭션에서 다시 처리한다.
                    stockService.createFor(id, 3L);
                }
                return null;
            })).toList();
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(stockRepository.findByProductId(3L).orElseThrow().getQuantity()).isZero();
        assertThat(processedMessageRepository.count()).isEqualTo(sameMessageId ? 1 : 2);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }
}
