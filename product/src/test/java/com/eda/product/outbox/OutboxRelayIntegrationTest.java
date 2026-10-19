package com.eda.product.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.eda.common.message.MessageHeader;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.common.outbox.OutboxWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = "outbox.relay.test")
class OutboxRelayIntegrationTest {

    record SampleCreated(int a) {
    }

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    @DisplayName("트랜잭션 안에서 write 한 메시지를 relay 가 Kafka 로 보내고 PUBLISHED 로 바꾼다")
    void relay() {
        // when
        Long id = new TransactionTemplate(transactionManager).execute(status ->
                outboxWriter.write("outbox.relay.test", "k1", new SampleCreated(1)).getId());

        // then
        ConsumerRecord<String, String> record = pollFirst("outbox.relay.test");
        assertThat(record.key()).isEqualTo("k1");
        assertThat(record.value()).isEqualTo("{\"a\":1}");
        assertThat(new String(record.headers().lastHeader(MessageHeader.MESSAGE_TYPE).value(), StandardCharsets.UTF_8))
                .isEqualTo("SampleCreated");
        assertThat(record.headers().lastHeader(MessageHeader.MESSAGE_ID)).isNotNull();
        assertThat(record.headers().lastHeader(MessageHeader.OCCURRED_AT)).isNotNull();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(outboxRepository.findById(id).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PUBLISHED));
    }

    @Test
    @DisplayName("트랜잭션 없이 write 를 호출하면 예외가 난다")
    void requireTransaction() {
        assertThatThrownBy(() -> outboxWriter.write("outbox.relay.test", "k1", new SampleCreated(1)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private ConsumerRecord<String, String> pollFirst(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> records = new ArrayList<>();
            await().atMost(Duration.ofSeconds(15)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
                return !records.isEmpty();
            });
            return records.getFirst();
        }
    }
}
