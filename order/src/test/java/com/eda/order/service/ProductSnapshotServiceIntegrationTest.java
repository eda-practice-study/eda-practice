package com.eda.order.service;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.message.order.ProductSnapshotCreated;
import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.order.domain.ProcessedMessageRepository;
import com.eda.order.domain.ProductSnapshotRepository;
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
    @DisplayName("이력·스냅샷·Outbox를 커밋하고 다른 생성 메시지에도 기존 이름과 가격을 유지한다")
    void createAndRedeliver() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var event = new ProductCreated(1L, "키보드", 30000);

        assertThat(service.createFor(first, event)).isTrue();
        assertThat(service.createFor(first, event)).isFalse();
        assertThat(service.createFor(second, new ProductCreated(1L, "바뀐 이름", 50000))).isFalse();

        assertThat(snapshotRepository.count()).isEqualTo(1);
        var snapshot = snapshotRepository.findByProductId(1L).orElseThrow();
        assertThat(snapshot.getName()).isEqualTo("키보드");
        assertThat(snapshot.getPrice()).isEqualByComparingTo("30000");
        assertThat(processedMessageRepository.count()).isEqualTo(2);
        var history = processedMessageRepository.findById(first).orElseThrow();
        assertThat(history.getProcessedAt()).isNotNull();
        assertThat(history.isNew()).isFalse();
        var messages = outboxRepository.findAll();
        assertThat(messages).hasSize(1);
        var message = messages.getFirst();
        assertThat(message.getTopic()).isEqualTo("order.events");
        assertThat(message.getMessageKey()).isEqualTo("1");
        assertThat(message.getMessageType()).isEqualTo("ProductSnapshotCreated");
        assertThat(message.getMessageId()).isNotEqualTo(first);
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(jsonMapper.readValue(message.getPayload(), ProductSnapshotCreated.class))
                .isEqualTo(new ProductSnapshotCreated(1L, "키보드", 30000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"product_snapshot", "outbox_message"})
    @DisplayName("스냅샷 또는 Outbox INSERT 실패 시 세 데이터를 함께 롤백하고 재시도한다")
    void rollbackAndRetry(String table) {
        UUID id = UUID.randomUUID();
        String condition = table.equals("product_snapshot") ? "product_id <> 2" : "topic <> 'order.events'";
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("동시 중복에도 스냅샷과 생성 이벤트는 한 건만 커밋한다")
    void concurrentDelivery(boolean sameMessageId) throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = sameMessageId ? first : UUID.randomUUID();
        var event = new ProductCreated(3L, "모니터", 250000);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.Stream.of(first, second).map(id -> executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    service.createFor(id, event);
                } catch (DataIntegrityViolationException ex) {
                    service.createFor(id, event);
                }
                return null;
            })).toList();
            for (var future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(snapshotRepository.findByProductId(3L).orElseThrow().getPrice()).isEqualByComparingTo("250000");
        assertThat(processedMessageRepository.count()).isEqualTo(sameMessageId ? 1 : 2);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }
}
