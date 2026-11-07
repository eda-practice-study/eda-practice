package com.eda.order.listener;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eda.common.message.Message;
import com.eda.common.message.MessageHeader;
import com.eda.common.message.order.ProductSnapshotCreated;
import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.order.domain.ProcessedMessageRepository;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.domain.ProductSnapshotRepository;
import com.eda.order.service.ProductSnapshotService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-kafka;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "outbox.enabled=true"
})
@EmbeddedKafka(partitions = 1, topics = {"product.events", "order.events", "order.product.events.DLT"})
@DirtiesContext
class ProductEventListenerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private ProductSnapshotRepository snapshotRepository;
    @Autowired
    private ProcessedMessageRepository processedMessageRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private JsonMapper jsonMapper;
    @MockitoSpyBean
    private ProductSnapshotService service;
    @MockitoSpyBean
    private DeadLetterPublishingRecoverer recoverer;

    @Test
    @DisplayName("새 상품 이벤트를 받으면 스냅샷을 저장하고 생성 이벤트를 발행한다")
    void on_shouldPublishSnapshot_whenProductNew() throws Exception {
        UUID id = UUID.randomUUID();
        RecordMetadata metadata = kafkaTemplate.send(event(id, 101L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        ConsumerRecord<String, String> record = awaitRecord("order.events", "101", null);
        Message<ProductSnapshotCreated> message = Message.from(record, ProductSnapshotCreated.class, jsonMapper);
        assertThat(message.payload()).isEqualTo(new ProductSnapshotCreated(101L, "키보드", 30000));
        assertThat(message.messageType()).isEqualTo("ProductSnapshotCreated");
        assertThat(message.messageId()).isNotEqualTo(id);
        OutboxMessage outbox = outboxRepository.findAll().stream().filter(row -> row.getMessageKey().equals("101")).findFirst().orElseThrow();
        assertThat(message.messageId()).isEqualTo(outbox.getMessageId());
        assertThat(message.occurredAt()).isEqualTo(outbox.getOccurredAt());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxRepository.findById(outbox.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED));

        ProductSnapshot snapshot = snapshotRepository.findByProductId(101L).orElseThrow();
        assertThat(snapshot.getName()).isEqualTo("키보드");
        assertThat(snapshot.getPrice()).isEqualByComparingTo("30000");
        assertThat(processedMessageRepository.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("같은 상품 생성 메시지를 다시 받으면 스냅샷과 생성 이벤트를 추가하지 않는다")
    void on_shouldSkip_whenMessageAlreadyProcessed() throws Exception {
        UUID id = UUID.randomUUID();
        RecordMetadata firstMetadata = kafkaTemplate.send(event(id, 111L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(firstMetadata)).isGreaterThan(firstMetadata.offset()));

        RecordMetadata metadata = kafkaTemplate.send(event(id, 111L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        ProductSnapshot snapshot = snapshotRepository.findByProductId(111L).orElseThrow();
        assertThat(snapshot.getName()).isEqualTo("키보드");
        assertThat(snapshot.getPrice()).isEqualByComparingTo("30000");
        assertThat(outboxRepository.findAll().stream().filter(row -> row.getMessageKey().equals("111"))).hasSize(1);
        verify(service, times(2)).createFor(id, new ProductCreated(111L, "키보드", 30000));
    }

    @Test
    @DisplayName("다른 메시지로 기존 상품을 받아도 스냅샷의 이름과 가격을 바꾸지 않는다")
    void on_shouldPreserveSnapshot_whenProductAlreadyExists() throws Exception {
        UUID firstId = UUID.randomUUID();
        RecordMetadata firstMetadata = kafkaTemplate.send(event(firstId, 112L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(firstMetadata)).isGreaterThan(firstMetadata.offset()));
        UUID incomingId = UUID.randomUUID();
        var changed = new ProducerRecord<String, String>("product.events", 0, "112",
                "{\"productId\":112,\"name\":\"바뀐 이름\",\"price\":50000}");
        event(incomingId, 112L).headers().forEach(header -> changed.headers().add(header));

        RecordMetadata metadata = kafkaTemplate.send(changed).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        ProductSnapshot snapshot = snapshotRepository.findByProductId(112L).orElseThrow();
        assertThat(snapshot.getName()).isEqualTo("키보드");
        assertThat(snapshot.getPrice()).isEqualByComparingTo("30000");
        assertThat(processedMessageRepository.existsById(incomingId)).isTrue();
        assertThat(outboxRepository.findAll().stream().filter(row -> row.getMessageKey().equals("112"))).hasSize(1);
    }

    @Test
    @DisplayName("일시적 저장 장애는 새 트랜잭션에서 재시도 후 성공한다")
    void on_shouldRetry_whenDatabaseRecovers() throws Exception {
        UUID id = UUID.randomUUID();
        var payload = new ProductCreated(102L, "키보드", 30000);
        doThrow(new DataAccessResourceFailureException("temporary outage"))
                .doCallRealMethod().when(service).createFor(id, payload);

        RecordMetadata metadata = kafkaTemplate.send(event(id, 102L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(snapshotRepository.existsByProductId(102L)).isTrue();
        assertThat(processedMessageRepository.existsById(id)).isTrue();
        verify(service, times(2)).createFor(id, payload);
        verify(recoverer, never()).accept(any(), any(), any());
    }

    @Test
    @DisplayName("3회 재시도 후에도 실패하면 원본 정보를 DLT에 보관하고 다음 이벤트를 처리한다")
    void on_shouldContinue_whenRetriesExhausted() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new DataAccessResourceFailureException("persistent outage"))
                .when(service).createFor(id, new ProductCreated(103L, "키보드", 30000));
        ProducerRecord<String, String> original = event(id, 103L);
        kafkaTemplate.send(original).get(10, TimeUnit.SECONDS);
        UUID nextId = UUID.randomUUID();
        RecordMetadata metadata = kafkaTemplate.send(event(nextId, 104L)).get(10, TimeUnit.SECONDS).getRecordMetadata();

        ConsumerRecord<String, String> deadLetter = awaitRecord("order.product.events.DLT", "103", id);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(deadLetter.value()).isEqualTo(original.value());
        assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        assertThat(deadLetter.headers().lastHeader(MessageHeader.MESSAGE_TYPE)).isNotNull();
        assertThat(deadLetter.headers().lastHeader(MessageHeader.OCCURRED_AT)).isNotNull();
        assertThat(snapshotRepository.existsByProductId(103L)).isFalse();
        assertThat(processedMessageRepository.existsById(id)).isFalse();
        assertThat(outboxRepository.findAll().stream().filter(row -> row.getMessageKey().equals("103"))).isEmpty();
        assertThat(snapshotRepository.existsByProductId(104L)).isTrue();
        verify(service, times(4)).createFor(id, new ProductCreated(103L, "키보드", 30000));
    }

    @Test
    @DisplayName("잘못된 JSON은 서비스를 호출하지 않고 즉시 DLT로 보낸다")
    void on_shouldSendToDlt_whenJsonMalformed() throws Exception {
        UUID id = UUID.randomUUID();
        var malformed = new ProducerRecord<String, String>("product.events", 0, "105", "{");
        event(id, 105L).headers().forEach(header -> malformed.headers().add(header));

        RecordMetadata metadata = kafkaTemplate.send(malformed).get(10, TimeUnit.SECONDS).getRecordMetadata();
        assertThat(awaitRecord("order.product.events.DLT", "105", id).value()).isEqualTo("{");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(processedMessageRepository.existsById(id)).isFalse();
        verifyNoInteractions(service);
        verify(recoverer, times(1)).accept(any(), any(), any());
    }

    @Test
    @DisplayName("잘못된 상품 식별자는 재시도하지 않고 DLT로 보낸다")
    void on_shouldSendToDlt_whenProductIdInvalid() throws Exception {
        UUID id = UUID.randomUUID();
        RecordMetadata metadata = kafkaTemplate.send(event(id, 0L)).get(10, TimeUnit.SECONDS).getRecordMetadata();

        awaitRecord("order.product.events.DLT", "0", id);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(processedMessageRepository.existsById(id)).isFalse();
        assertThat(snapshotRepository.existsByProductId(0L)).isFalse();
        verify(service, times(1)).createFor(id, new ProductCreated(0L, "키보드", 30000));
        verify(recoverer, times(1)).accept(any(), any(), any());
    }

    @Test
    @DisplayName("DLT 발행 실패 시 원본 오프셋을 넘기지 않고 복구 후 재처리한다")
    void on_shouldRetainOffset_whenDltPublishFails() throws Exception {
        UUID id = UUID.randomUUID();
        var malformed = new ProducerRecord<String, String>("product.events", 0, "106", "{");
        event(id, 106L).headers().forEach(header -> malformed.headers().add(header));
        var allowPublish = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (!allowPublish.get()) {
                throw new IllegalStateException("DLT unavailable");
            }
            return invocation.callRealMethod();
        }).when(recoverer).accept(any(ConsumerRecord.class), any(Consumer.class), any(Exception.class));

        RecordMetadata metadata = kafkaTemplate.send(malformed).get(10, TimeUnit.SECONDS).getRecordMetadata();
        try {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    verify(recoverer, atLeast(2)).accept(any(), any(), any()));
            try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", broker.getBrokersAsString()))) {
                OffsetAndMetadata offset = admin.listConsumerGroupOffsets("order-service").partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS).get(new TopicPartition("product.events", 0));
                assertThat(offset == null || offset.offset() <= metadata.offset()).isTrue();
            }
        } finally {
            allowPublish.set(true);
        }

        assertThat(awaitRecord("order.product.events.DLT", "106", id).value()).isEqualTo("{");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));
        assertThat(processedMessageRepository.existsById(id)).isFalse();
    }

    private ProducerRecord<String, String> event(UUID id, Long productId) {
        var record = new ProducerRecord<String, String>("product.events", 0, productId.toString(),
                "{\"productId\":" + productId + ",\"name\":\"키보드\",\"price\":30000}");
        record.headers().add(MessageHeader.MESSAGE_ID, id.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.MESSAGE_TYPE, "ProductCreated".getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.OCCURRED_AT, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private ConsumerRecord<String, String> awaitRecord(String topic, String key, UUID messageId) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "order-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of(topic));
            return await().atMost(Duration.ofSeconds(20)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    Header header = record.headers().lastHeader(MessageHeader.MESSAGE_ID);
                    if (key.equals(record.key()) && (messageId == null || (header != null
                            && messageId.toString().equals(new String(header.value(), StandardCharsets.UTF_8))))) {
                        return record;
                    }
                }
                return null;
            }, Objects::nonNull);
        }
    }

    private long committedOffset(RecordMetadata metadata) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", broker.getBrokersAsString()))) {
            OffsetAndMetadata offset = admin.listConsumerGroupOffsets("order-service").partitionsToOffsetAndMetadata()
                    .get(5, TimeUnit.SECONDS).get(new TopicPartition(metadata.topic(), metadata.partition()));
            return offset == null ? -1L : offset.offset();
        }
    }
}
