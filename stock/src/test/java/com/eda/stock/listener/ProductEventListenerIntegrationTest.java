package com.eda.stock.listener;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eda.common.message.MessageHeader;
import com.eda.stock.domain.ProcessedMessageRepository;
import com.eda.stock.domain.StockRepository;
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
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
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

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kafka-stock;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true"
})
@EmbeddedKafka(partitions = 1, topics = {"product.events", "stock.product.events.DLT"})
@DirtiesContext
class ProductEventListenerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private ProcessedMessageRepository processedMessageRepository;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @MockitoSpyBean
    private StockService stockService;
    @MockitoSpyBean
    private DeadLetterPublishingRecoverer recoverer;

    @Test
    @DisplayName("Kafka로 받은 이벤트는 초기 재고를 만들고 재수신해도 기존 수량을 보존한다")
    void receiveAndRedeliver() throws Exception {
        UUID messageId = UUID.randomUUID();
        kafkaTemplate.send(event(messageId, 101L)).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(processedMessageRepository.existsById(messageId)).isTrue());
        var stock = stockRepository.findByProductId(101L).orElseThrow();
        assertThat(stock.getQuantity()).isZero();
        stock.increase(10);
        stockRepository.saveAndFlush(stock);

        kafkaTemplate.send(event(messageId, 101L)).get(10, TimeUnit.SECONDS);
        UUID anotherMessage = UUID.randomUUID();
        var metadata = kafkaTemplate.send(event(anotherMessage, 101L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        awaitCommitted(metadata);

        assertThat(stockRepository.findByProductId(101L).orElseThrow().getQuantity()).isEqualTo(10);
        assertThat(processedMessageRepository.existsById(anotherMessage)).isTrue();
        verify(stockService, times(2)).createFor(messageId, 101L);
    }

    @Test
    @DisplayName("일시적 저장 실패는 재시도 후 성공하고 DLT로 보내지 않는다")
    void retryThenSucceed() throws Exception {
        UUID messageId = UUID.randomUUID();
        doThrow(new DataAccessResourceFailureException("temporary outage"))
                .doCallRealMethod().when(stockService).createFor(messageId, 102L);

        var metadata = kafkaTemplate.send(event(messageId, 102L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        awaitCommitted(metadata);

        assertThat(stockRepository.findByProductId(102L).orElseThrow().getQuantity()).isZero();
        assertThat(processedMessageRepository.existsById(messageId)).isTrue();
        verify(stockService, times(2)).createFor(messageId, 102L);
        verify(recoverer, never()).accept(any(), any(), any());
    }

    @Test
    @DisplayName("저장 실패를 3회 재시도한 뒤 DLT로 보내고 뒤의 정상 이벤트를 처리한다")
    void exhaustedRetries() throws Exception {
        UUID messageId = UUID.randomUUID();
        doThrow(new DataAccessResourceFailureException("persistent outage"))
                .when(stockService).createFor(messageId, 103L);
        var original = event(messageId, 103L);

        kafkaTemplate.send(original).get(10, TimeUnit.SECONDS);
        UUID nextId = UUID.randomUUID();
        var metadata = kafkaTemplate.send(event(nextId, 104L)).get(10, TimeUnit.SECONDS).getRecordMetadata();
        var deadLetter = awaitDeadLetter(messageId);
        awaitCommitted(metadata);

        assertThat(deadLetter.key()).isEqualTo(original.key());
        assertThat(deadLetter.value()).isEqualTo(original.value());
        assertThat(new String(deadLetter.headers().lastHeader(MessageHeader.MESSAGE_TYPE).value(), StandardCharsets.UTF_8))
                .isEqualTo("ProductCreated");
        assertThat(deadLetter.headers().lastHeader(MessageHeader.OCCURRED_AT)).isNotNull();
        assertThat(deadLetter.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        assertThat(stockRepository.existsByProductId(103L)).isFalse();
        assertThat(processedMessageRepository.existsById(messageId)).isFalse();
        assertThat(stockRepository.existsByProductId(104L)).isTrue();
        verify(stockService, times(4)).createFor(messageId, 103L);
    }

    @Test
    @DisplayName("잘못된 JSON은 재고를 저장하지 않고 바로 DLT로 보낸다")
    void malformedEvent() throws Exception {
        UUID messageId = UUID.randomUUID();
        var original = event(messageId, 105L);
        var malformed = new ProducerRecord<String, String>("product.events", 0, "105", "{");
        original.headers().forEach(header -> malformed.headers().add(header));

        var metadata = kafkaTemplate.send(malformed).get(10, TimeUnit.SECONDS).getRecordMetadata();
        assertThat(awaitDeadLetter(messageId).value()).isEqualTo("{");
        awaitCommitted(metadata);

        assertThat(stockRepository.existsByProductId(105L)).isFalse();
        assertThat(processedMessageRepository.existsById(messageId)).isFalse();
        verifyNoInteractions(stockService);
        verify(recoverer, times(1)).accept(any(), any(), any());
    }

    @Test
    @DisplayName("DLT 발행 실패 시 원본 오프셋을 넘기지 않고 재처리한다")
    void deadLetterPublishFailure() throws Exception {
        UUID messageId = UUID.randomUUID();
        var original = event(messageId, 106L);
        var malformed = new ProducerRecord<String, String>("product.events", 0, "106", "{");
        original.headers().forEach(header -> malformed.headers().add(header));
        var allowPublish = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (!allowPublish.get()) {
                throw new IllegalStateException("DLT unavailable");
            }
            return invocation.callRealMethod();
        }).when(recoverer).accept(any(ConsumerRecord.class), any(Consumer.class), any(Exception.class));

        var metadata = kafkaTemplate.send(malformed).get(10, TimeUnit.SECONDS).getRecordMetadata();
        try {
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    verify(recoverer, atLeast(2)).accept(any(), any(), any()));
            try (var admin = AdminClient.create(Map.of("bootstrap.servers", broker.getBrokersAsString()))) {
                var offset = admin.listConsumerGroupOffsets("stock-service").partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS).get(new TopicPartition("product.events", 0));
                assertThat(offset == null || offset.offset() <= metadata.offset()).isTrue();
            }
            assertThat(processedMessageRepository.existsById(messageId)).isFalse();
        } finally {
            allowPublish.set(true);
        }

        assertThat(awaitDeadLetter(messageId).value()).isEqualTo("{");
        awaitCommitted(metadata);
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
                for (var record : consumer.poll(Duration.ofMillis(200))) {
                    var header = record.headers().lastHeader(MessageHeader.MESSAGE_ID);
                    if (header != null && messageId.toString().equals(new String(header.value(), StandardCharsets.UTF_8))) {
                        return record;
                    }
                }
                return null;
            }, java.util.Objects::nonNull);
        }
    }

    private void awaitCommitted(RecordMetadata metadata) {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", broker.getBrokersAsString()))) {
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                var offset = admin.listConsumerGroupOffsets("stock-service").partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS).get(new TopicPartition(metadata.topic(), metadata.partition()));
                return offset != null && offset.offset() > metadata.offset();
            });
        }
    }
}
