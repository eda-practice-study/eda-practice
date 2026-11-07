package com.eda.order.service;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.message.order.ProductSnapshotCreated;
import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.order.domain.ProcessedMessage;
import com.eda.order.domain.ProcessedMessageRepository;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.domain.ProductSnapshotRepository;
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
        "spring.jpa.properties.hibernate.default_schema=orders",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class ProductSnapshotServiceIntegrationTest {

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
    private ProductSnapshotService service;
    @Autowired
    private ProductSnapshotRepository snapshotRepository;
    @Autowired
    private ProcessedMessageRepository processedMessageRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        processedMessageRepository.deleteAll();
        snapshotRepository.deleteAll();
    }

    @Test
    @DisplayName("새 상품이면 스냅샷과 처리 이력 및 생성 이벤트를 함께 저장한다")
    void createFor_shouldPersistEvent_whenProductNew() {
        UUID messageId = UUID.randomUUID();
        var event = new ProductCreated(1L, "키보드", 30000);

        boolean created = service.createFor(messageId, event);

        assertThat(created).isTrue();
        ProductSnapshot snapshot = snapshotRepository.findByProductId(1L).orElseThrow();
        assertThat(snapshot.getName()).isEqualTo("키보드");
        assertThat(snapshot.getPrice()).isEqualByComparingTo("30000");
        ProcessedMessage history = processedMessageRepository.findById(messageId).orElseThrow();
        assertThat(history.getProcessedAt()).isNotNull();
        assertThat(history.isNew()).isFalse();
        List<OutboxMessage> messages = outboxRepository.findAll();
        assertThat(messages).hasSize(1);
        OutboxMessage message = messages.getFirst();
        assertThat(message.getTopic()).isEqualTo("order.events");
        assertThat(message.getMessageKey()).isEqualTo("1");
        assertThat(message.getMessageType()).isEqualTo("ProductSnapshotCreated");
        assertThat(message.getMessageId()).isNotEqualTo(messageId);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(jsonMapper.readValue(message.getPayload(), ProductSnapshotCreated.class))
                .isEqualTo(new ProductSnapshotCreated(1L, "키보드", 30000));
    }

    @Test
    @DisplayName("같은 메시지를 다시 받으면 스냅샷과 생성 이벤트를 추가하지 않는다")
    void createFor_shouldSkip_whenMessageAlreadyProcessed() {
        UUID messageId = UUID.randomUUID();
        var event = new ProductCreated(1L, "키보드", 30000);
        service.createFor(messageId, event);

        boolean created = service.createFor(messageId, event);

        assertThat(created).isFalse();
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(processedMessageRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 메시지로 기존 상품을 받으면 이름과 가격을 보존하고 새 처리 이력만 저장한다")
    void createFor_shouldPreserveSnapshot_whenProductAlreadyExists() {
        service.createFor(UUID.randomUUID(), new ProductCreated(1L, "키보드", 30000));
        UUID messageId = UUID.randomUUID();
        var changed = new ProductCreated(1L, "바뀐 이름", 50000);

        boolean created = service.createFor(messageId, changed);

        assertThat(created).isFalse();
        assertThat(snapshotRepository.count()).isEqualTo(1);
        ProductSnapshot snapshot = snapshotRepository.findByProductId(1L).orElseThrow();
        assertThat(snapshot.getName()).isEqualTo("키보드");
        assertThat(snapshot.getPrice()).isEqualByComparingTo("30000");
        assertThat(processedMessageRepository.existsById(messageId)).isTrue();
        assertThat(processedMessageRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("스냅샷 저장 장애를 해결하면 같은 메시지를 다시 처리할 수 있다")
    void createFor_shouldRecover_whenSnapshotSaveRestored() {
        UUID id = UUID.randomUUID();
        String table = "product_snapshot";
        String condition = "product_id <> 2";
        jdbcTemplate.execute("alter table orders." + table + " add constraint test_reject_insert check (" + condition + ")");
        try {
            assertThatThrownBy(() -> service.createFor(id, new ProductCreated(2L, "마우스", 15000)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(processedMessageRepository.existsById(id)).isFalse();
            assertThat(snapshotRepository.existsByProductId(2L)).isFalse();
            assertThat(outboxRepository.count()).isZero();
        } finally {
            jdbcTemplate.execute("alter table orders." + table + " drop constraint test_reject_insert");
        }

        assertThat(service.createFor(id, new ProductCreated(2L, "마우스", 15000))).isTrue();

        assertThat(processedMessageRepository.existsById(id)).isTrue();
        assertThat(snapshotRepository.findByProductId(2L).orElseThrow().getName()).isEqualTo("마우스");
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이벤트 저장 장애를 해결하면 같은 메시지를 다시 처리할 수 있다")
    void createFor_shouldRecover_whenOutboxSaveRestored() {
        UUID id = UUID.randomUUID();
        String table = "outbox_message";
        String condition = "topic <> 'order.events'";
        jdbcTemplate.execute("alter table orders." + table + " add constraint test_reject_insert check (" + condition + ")");
        try {
            assertThatThrownBy(() -> service.createFor(id, new ProductCreated(2L, "마우스", 15000)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(processedMessageRepository.existsById(id)).isFalse();
            assertThat(snapshotRepository.existsByProductId(2L)).isFalse();
            assertThat(outboxRepository.count()).isZero();
        } finally {
            jdbcTemplate.execute("alter table orders." + table + " drop constraint test_reject_insert");
        }

        assertThat(service.createFor(id, new ProductCreated(2L, "마우스", 15000))).isTrue();

        assertThat(processedMessageRepository.existsById(id)).isTrue();
        assertThat(snapshotRepository.findByProductId(2L).orElseThrow().getName()).isEqualTo("마우스");
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 메시지를 동시에 받으면 한 건의 생성 이벤트만 저장한다")
    void createFor_shouldCreateOnce_whenSameMessageConcurrent() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = first;
        var event = new ProductCreated(3L, "모니터", 250000);
        var barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = java.util.stream.Stream.of(first, second).map(id -> executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    service.createFor(id, event);
                } catch (DataIntegrityViolationException ex) {
                    service.createFor(id, event);
                }
                return null;
            })).toList();
            for (Future<Object> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(snapshotRepository.findByProductId(3L).orElseThrow().getPrice()).isEqualByComparingTo("250000");
        assertThat(processedMessageRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("서로 다른 메시지로 같은 상품을 동시에 받아도 한 건의 생성 이벤트만 저장한다")
    void createFor_shouldCreateOnce_whenSameProductConcurrent() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var event = new ProductCreated(3L, "모니터", 250000);
        var barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = java.util.stream.Stream.of(first, second).map(id -> executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    service.createFor(id, event);
                } catch (DataIntegrityViolationException ex) {
                    service.createFor(id, event);
                }
                return null;
            })).toList();
            for (Future<Object> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(snapshotRepository.findByProductId(3L).orElseThrow().getPrice()).isEqualByComparingTo("250000");
        assertThat(processedMessageRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }
}
