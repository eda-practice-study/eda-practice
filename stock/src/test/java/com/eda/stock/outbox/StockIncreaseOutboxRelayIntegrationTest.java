package com.eda.stock.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.eda.common.message.Message;
import com.eda.common.message.stock.StockIncreased;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infra.StockCache;
import com.eda.stock.service.StockIncreaseService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-increase-outbox;DB_CLOSE_DELAY=-1",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.enabled=true"
})
@EmbeddedKafka(partitions = 1, topics = "stock.events")
@DirtiesContext
class StockIncreaseOutboxRelayIntegrationTest {

    @Autowired
    private StockIncreaseService stockService;
    @Autowired
    private StockRepository stockRepository;
    @MockitoBean
    private StockCache stockCache;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("재고 증가가 커밋되면 Relay가 재고 증가 이벤트를 Kafka로 발행한다")
    void relay_shouldPublishStockIncreased_whenOutboxCommitted() {
        UUID idempotencyKey = UUID.randomUUID();

        stockRepository.saveAndFlush(Stock.createFor(1L));
        stockService.increaseForRequest(idempotencyKey.toString(), 1L, 100);

        List<OutboxMessage> outbox = outboxRepository.findAll();
        assertThat(outbox).hasSize(1);
        OutboxMessage expected = outbox.getFirst();
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "stock-increased-test-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of("stock.events"));
            ConsumerRecord<String, String> record = await().atMost(Duration.ofSeconds(15)).until(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                return records.isEmpty() ? null : records.iterator().next();
            }, Objects::nonNull);

            assertThat(record.key()).isEqualTo("1");
            Message<StockIncreased> message = Message.from(record, StockIncreased.class, jsonMapper);
            assertThat(message.payload()).isEqualTo(new StockIncreased(1L, 100, 100));
            assertThat(message.messageType()).isEqualTo("StockIncreased");
            assertThat(message.messageId()).isEqualTo(expected.getMessageId()).isNotEqualTo(idempotencyKey);
            assertThat(message.occurredAt()).isEqualTo(expected.getOccurredAt());
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxMessage published = outboxRepository.findById(expected.getId()).orElseThrow();
            assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
            assertThat(published.getPublishedAt()).isNotNull();
        });
    }
}
