package com.eda.stock.listener;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eda.common.message.MessageHeader;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.stock.domain.ProcessedMessageRepository;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infra.StockCache;
import com.eda.stock.service.StockService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kafka-stock;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true"
})
@EmbeddedKafka(partitions = 1, topics = {"product.events", "stock.product.events.DLT"})
@DirtiesContext
@Testcontainers
class ProductEventListenerIntegrationTest {

    @Container
    static final GenericContainer<?> valkey = new GenericContainer<>("valkey/valkey:9.0-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", valkey::getHost);
        registry.add("spring.data.redis.port", () -> valkey.getMappedPort(6379));
        registry.add("spring.data.redis.connect-timeout", () -> "500ms");
        registry.add("spring.data.redis.timeout", () -> "500ms");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ProcessedMessageRepository processedMessageRepository;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @MockitoSpyBean
    private StockService stockService;
    @MockitoSpyBean
    private DeadLetterPublishingRecoverer recoverer;
    @MockitoSpyBean
    private StockCache stockCache;
    @MockitoSpyBean
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("새 상품 이벤트를 받으면 DB 커밋 후 Redis에 초기 재고를 저장한다")
    void on_shouldInitializeRedis_whenProductNew() throws Exception {
        UUID messageId = UUID.randomUUID();
        var databaseCommitted = new AtomicBoolean(false);
        doAnswer(invocation -> {
            databaseCommitted.set(!TransactionSynchronizationManager.isActualTransactionActive()
                    && processedMessageRepository.existsById(messageId)
                    && stockRepository.existsByProductId(101L));
            return invocation.callRealMethod();
        }).when(stockCache).initialize(101L);
        RecordMetadata firstMetadata = kafkaTemplate.send(event(messageId, 101L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(firstMetadata)).isGreaterThan(firstMetadata.offset()));
        assertThat(databaseCommitted).isTrue();
        assertThat(redisTemplate.opsForValue().get("stock:available:101")).isEqualTo("0");
        assertThat(redisTemplate.getExpire("stock:available:101")).isEqualTo(-1L);
        Stock stock = stockRepository.findByProductId(101L).orElseThrow();
        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("같은 상품 생성 메시지를 다시 받아도 기존 재고와 Redis 수량을 유지한다")
    void on_shouldPreserveStock_whenMessageAlreadyProcessed() throws Exception {
        UUID messageId = UUID.randomUUID();
        RecordMetadata firstMetadata = kafkaTemplate.send(event(messageId, 111L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(firstMetadata)).isGreaterThan(firstMetadata.offset()));
        Stock stock = stockRepository.findByProductId(111L).orElseThrow();
        stock.increase(10);
        stockRepository.saveAndFlush(stock);
        redisTemplate.opsForValue().set("stock:available:111", "10");
        UUID incomingId = messageId;

        RecordMetadata metadata = kafkaTemplate.send(event(incomingId, 111L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(stockRepository.findByProductId(111L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(redisTemplate.opsForValue().get("stock:available:111")).isEqualTo("10");
        assertThat(processedMessageRepository.existsById(incomingId)).isTrue();
        assertThat(outboxRepository.findAll().stream().filter(row -> row.getMessageKey().equals("111"))).hasSize(1);
        verify(stockCache, times(1)).initialize(111L);
    }

    @Test
    @DisplayName("다른 메시지로 기존 상품을 받아도 재고와 Redis 수량을 유지한다")
    void on_shouldPreserveStock_whenProductAlreadyExists() throws Exception {
        UUID messageId = UUID.randomUUID();
        RecordMetadata firstMetadata = kafkaTemplate.send(event(messageId, 112L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(firstMetadata)).isGreaterThan(firstMetadata.offset()));
        Stock stock = stockRepository.findByProductId(112L).orElseThrow();
        stock.increase(10);
        stockRepository.saveAndFlush(stock);
        redisTemplate.opsForValue().set("stock:available:112", "10");
        UUID incomingId = UUID.randomUUID();

        RecordMetadata metadata = kafkaTemplate.send(event(incomingId, 112L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(stockRepository.findByProductId(112L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(redisTemplate.opsForValue().get("stock:available:112")).isEqualTo("10");
        assertThat(processedMessageRepository.existsById(incomingId)).isTrue();
        assertThat(outboxRepository.findAll().stream().filter(row -> row.getMessageKey().equals("112"))).hasSize(1);
        verify(stockCache, times(1)).initialize(112L);
    }

    @Test
    @DisplayName("새 Stock이어도 Redis 키가 이미 있으면 초기화로 덮어쓰지 않는다")
    void on_shouldPreserveCache_whenKeyAlreadyExists() throws Exception {
        UUID messageId = UUID.randomUUID();
        redisTemplate.opsForValue().set("stock:available:107", "25");

        RecordMetadata metadata = kafkaTemplate.send(event(messageId, 107L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(stockRepository.findByProductId(107L).orElseThrow().getQuantity()).isZero();
        assertThat(redisTemplate.opsForValue().get("stock:available:107")).isEqualTo("25");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Redis 갱신 실패에도 DB와 이력을 커밋하고 Kafka 재시도나 DLT 없이 완료한다")
    void on_shouldCommitDatabase_whenRedisWriteFails() throws Exception {
        UUID messageId = UUID.randomUUID();
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(values.setIfAbsent("stock:available:108", "0"))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));
        doReturn(values).when(redisTemplate).opsForValue();

        RecordMetadata metadata = kafkaTemplate.send(event(messageId, 108L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(stockRepository.findByProductId(108L).orElseThrow().getQuantity()).isZero();
        assertThat(processedMessageRepository.existsById(messageId)).isTrue();
        verify(stockService, times(1)).createFor(messageId, 108L);
        List<OutboxMessage> events = outboxRepository.findAll().stream()
                .filter(event -> event.getMessageKey().equals("108") && event.getTopic().equals("stock.events"))
                .toList();
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getMessageType()).isEqualTo("StockCreated");
        assertThat(events.getFirst().getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(values, times(1)).setIfAbsent("stock:available:108", "0");
        verify(recoverer, never()).accept(any(), any(), any());
    }

    @Test
    @DisplayName("처리 완료 후 Redis 키가 유실되어도 이벤트 재수신으로 0을 다시 쓰지 않는다")
    void on_shouldSkipCache_whenProcessedKeyLost() throws Exception {
        UUID messageId = UUID.randomUUID();
        RecordMetadata firstMetadata = kafkaTemplate.send(event(messageId, 109L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(firstMetadata)).isGreaterThan(firstMetadata.offset()));
        redisTemplate.delete("stock:available:109");

        RecordMetadata metadata = kafkaTemplate.send(event(messageId, 109L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(stockRepository.existsByProductId(109L)).isTrue();
        assertThat(redisTemplate.hasKey("stock:available:109")).isFalse();
        verify(stockCache, times(1)).initialize(109L);
    }

    @Test
    @DisplayName("일시적 저장 실패는 재시도 후 성공하고 DLT로 보내지 않는다")
    void on_shouldRetry_whenDatabaseRecovers() throws Exception {
        UUID messageId = UUID.randomUUID();
        doThrow(new DataAccessResourceFailureException("temporary outage"))
                .doCallRealMethod().when(stockService).createFor(messageId, 102L);

        RecordMetadata metadata = kafkaTemplate.send(event(messageId, 102L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(stockRepository.findByProductId(102L).orElseThrow().getQuantity()).isZero();
        assertThat(processedMessageRepository.existsById(messageId)).isTrue();
        verify(stockService, times(2)).createFor(messageId, 102L);
        verify(recoverer, never()).accept(any(), any(), any());
    }

    @Test
    @DisplayName("저장 실패를 3회 재시도한 뒤 DLT로 보내고 뒤의 정상 이벤트를 처리한다")
    void on_shouldContinue_whenRetriesExhausted() throws Exception {
        UUID messageId = UUID.randomUUID();
        doThrow(new DataAccessResourceFailureException("persistent outage"))
                .when(stockService).createFor(messageId, 103L);
        ProducerRecord<String, String> original = event(messageId, 103L);

        kafkaTemplate.send(original).get(10, TimeUnit.SECONDS);
        UUID nextId = UUID.randomUUID();
        RecordMetadata metadata = kafkaTemplate.send(event(nextId, 104L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        ConsumerRecord<String, String> deadLetter = awaitDeadLetter(messageId);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(deadLetter.key()).isEqualTo(original.key());
        assertThat(deadLetter.value()).isEqualTo(original.value());
        assertThat(new String(deadLetter.headers().lastHeader(MessageHeader.MESSAGE_TYPE).value(), StandardCharsets.UTF_8))
                .isEqualTo("ProductCreated");
        assertThat(deadLetter.headers().lastHeader(MessageHeader.OCCURRED_AT)).isNotNull();
        assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        assertThat(stockRepository.existsByProductId(103L)).isFalse();
        verify(stockCache, never()).initialize(103L);
        assertThat(processedMessageRepository.existsById(messageId)).isFalse();
        assertThat(stockRepository.existsByProductId(104L)).isTrue();
        verify(stockService, times(4)).createFor(messageId, 103L);
    }

    @Test
    @DisplayName("잘못된 JSON은 재고를 저장하지 않고 바로 DLT로 보낸다")
    void on_shouldSendToDlt_whenJsonMalformed() throws Exception {
        UUID messageId = UUID.randomUUID();
        ProducerRecord<String, String> original = event(messageId, 105L);
        var malformed = new ProducerRecord<String, String>("product.events", 0, "105", "{");
        original.headers().forEach(header -> malformed.headers().add(header));

        RecordMetadata metadata = kafkaTemplate.send(malformed).get(10, TimeUnit.SECONDS).getRecordMetadata();
        assertThat(awaitDeadLetter(messageId).value()).isEqualTo("{");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));

        assertThat(stockRepository.existsByProductId(105L)).isFalse();
        assertThat(processedMessageRepository.existsById(messageId)).isFalse();
        verifyNoInteractions(stockService);
        verify(recoverer, times(1)).accept(any(), any(), any());
    }

    @Test
    @DisplayName("DLT 발행 실패 시 원본 오프셋을 넘기지 않고 재처리한다")
    void on_shouldRetainOffset_whenDltPublishFails() throws Exception {
        UUID messageId = UUID.randomUUID();
        ProducerRecord<String, String> original = event(messageId, 106L);
        var malformed = new ProducerRecord<String, String>("product.events", 0, "106", "{");
        original.headers().forEach(header -> malformed.headers().add(header));
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
                OffsetAndMetadata offset = admin.listConsumerGroupOffsets("stock-service").partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS).get(new TopicPartition("product.events", 0));
                assertThat(offset == null || offset.offset() <= metadata.offset()).isTrue();
            }
            assertThat(processedMessageRepository.existsById(messageId)).isFalse();
        } finally {
            allowPublish.set(true);
        }

        assertThat(awaitDeadLetter(messageId).value()).isEqualTo("{");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(committedOffset(metadata)).isGreaterThan(metadata.offset()));
    }

    private ProducerRecord<String, String> event(UUID messageId, Long productId) {
        var record = new ProducerRecord<String, String>("product.events", 0, productId.toString(),
                "{\"productId\":" + productId + ",\"name\":\"키보드\",\"price\":30000}");
        record.headers().add(MessageHeader.MESSAGE_ID, messageId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.MESSAGE_TYPE, "ProductCreated".getBytes(StandardCharsets.UTF_8));
        record.headers().add(MessageHeader.OCCURRED_AT, Instant.now().toString().getBytes(StandardCharsets.UTF_8));
        return record;
    }

    private ConsumerRecord<String, String> awaitDeadLetter(UUID messageId) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of("stock.product.events.DLT"));
            return await().atMost(Duration.ofSeconds(20)).until(() -> {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(200))) {
                    Header header = record.headers().lastHeader(MessageHeader.MESSAGE_ID);
                    if (header != null && messageId.toString().equals(new String(header.value(), StandardCharsets.UTF_8))) {
                        return record;
                    }
                }
                return null;
            }, java.util.Objects::nonNull);
        }
    }

    private long committedOffset(RecordMetadata metadata) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", broker.getBrokersAsString()))) {
            OffsetAndMetadata offset = admin.listConsumerGroupOffsets("stock-service").partitionsToOffsetAndMetadata()
                    .get(5, TimeUnit.SECONDS).get(new TopicPartition(metadata.topic(), metadata.partition()));
            return offset == null ? -1L : offset.offset();
        }
    }
}
