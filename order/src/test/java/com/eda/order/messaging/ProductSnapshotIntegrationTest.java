package com.eda.order.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.eda.common.message.Message;
import com.eda.common.message.MessageHeader;
import com.eda.common.message.ProductCreated;
import com.eda.common.message.ProductSnapshotRegistered;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.order.domain.ProductSnapShotRepository;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

// Kafka 수신 대신 Listener를 직접 호출하여 메시지 변환·서비스·DB·Outbox 연결을 확인한다.
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:order-snapshot;DB_CLOSE_DELAY=-1")
class ProductSnapshotIntegrationTest {

    @Autowired
    private ProductEventListener listener;

    @Autowired
    private ProductSnapShotRepository snapshotRepository;

    @MockitoSpyBean
    private OutboxRepository outboxRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        snapshotRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 이벤트를 받으면 스냅샷과 PENDING 등록 이벤트를 함께 커밋한다")
    void registerWithOutbox() {
        listener.onProductCreated(record(new ProductCreated(1L, "키보드", 30000L)));

        assertThat(snapshotRepository.findAll()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getId()).isNotNull();
            assertThat(snapshot.getProductId()).isEqualTo(1L);
            assertThat(snapshot.getName()).isEqualTo("키보드");
            assertThat(snapshot.getPrice()).isEqualByComparingTo("30000");
            assertThat(snapshot.getCreatedAt()).isNotNull();
        });
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getTopic()).isEqualTo("order.events");
            assertThat(message.getMessageKey()).isEqualTo("1");
            assertThat(message.getMessageType()).isEqualTo("ProductSnapshotRegistered");
            assertThat(message.getMessageId()).isNotNull();
            assertThat(message.getOccurredAt()).isNotNull();
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getPublishedAt()).isNull();
            assertThat(jsonMapper.readValue(message.getPayload(), ProductSnapshotRegistered.class))
                    .isEqualTo(new ProductSnapshotRegistered(1L, "키보드", 30000L));
        });
    }

    @Test
    @DisplayName("같은 이벤트를 재수신해도 스냅샷과 등록 이벤트를 추가 생성하지 않는다")
    void redeliveryDoesNotCreateDuplicates() {
        var record = record(new ProductCreated(1L, "키보드", 30000L));
        listener.onProductCreated(record);
        var snapshotId = snapshotRepository.findByProductId(1L).orElseThrow().getId();
        var messageId = outboxRepository.findAll().getFirst().getMessageId();

        listener.onProductCreated(record);

        assertThat(snapshotRepository.findAll()).singleElement().satisfies(snapshot ->
                assertThat(snapshot.getId()).isEqualTo(snapshotId));
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message ->
                assertThat(message.getMessageId()).isEqualTo(messageId));
    }

    @Test
    @DisplayName("같은 상품 ID의 생성 이벤트를 다시 받아도 기존 이름과 가격을 덮어쓰지 않는다")
    void preserveExistingSnapshot() {
        listener.onProductCreated(record(new ProductCreated(1L, "키보드", 30000L)));
        var messageId = outboxRepository.findAll().getFirst().getMessageId();

        listener.onProductCreated(record(new ProductCreated(1L, "다른 이름", 40000L)));

        assertThat(snapshotRepository.findAll()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getName()).isEqualTo("키보드");
            assertThat(snapshot.getPrice()).isEqualByComparingTo("30000");
        });
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getMessageId()).isEqualTo(messageId);
            assertThat(jsonMapper.readValue(message.getPayload(), ProductSnapshotRegistered.class))
                    .isEqualTo(new ProductSnapshotRegistered(1L, "키보드", 30000L));
        });
    }

    @Test
    @DisplayName("Outbox 실패 시 스냅샷도 롤백하고 같은 메시지를 다시 처리하면 함께 저장한다")
    void rollbackAndReprocessAfterOutboxFailure() {
        var record = record(new ProductCreated(1L, "키보드", 30000L));
        var failure = new DataIntegrityViolationException("테스트용 Outbox 저장 실패");
        doAnswer(invocation -> {
            assertThat(snapshotRepository.count()).isEqualTo(1);
            throw failure;
        }).when(outboxRepository).save(any(OutboxMessage.class));

        assertThatThrownBy(() -> listener.onProductCreated(record)).isSameAs(failure);

        // 테스트 자체에 트랜잭션을 붙이지 않아 서비스 롤백 후의 상태를 조회한다.
        assertThat(snapshotRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();

        // 실패를 주입한 설정을 해제하여 실제 Repository 저장으로 복귀한다.
        reset(outboxRepository);

        // 자동 Kafka 재시도가 아니라, 같은 메시지를 직접 다시 전달하는 검증이다.
        listener.onProductCreated(record);

        assertThat(snapshotRepository.findByProductId(1L)).isPresent();
        assertThat(snapshotRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    private ConsumerRecord<String, String> record(ProductCreated payload) {
        Message<ProductCreated> message = Message.of(payload);
        var record = new ConsumerRecord<>(
                "product.events", 0, 0L, payload.productId().toString(),
                jsonMapper.writeValueAsString(payload));
        record.headers().add(MessageHeader.MESSAGE_ID,
                message.messageId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.MESSAGE_TYPE,
                message.messageType().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.OCCURRED_AT,
                message.occurredAt().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
