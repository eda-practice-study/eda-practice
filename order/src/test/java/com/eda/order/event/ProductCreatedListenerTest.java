package com.eda.order.event;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.eda.common.message.MessageHeader;
import com.eda.common.outbox.OutboxRepository;
import com.eda.order.domain.ProductSnapshotRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.enabled=false"
})
@EmbeddedKafka(partitions = 1, topics = "product.events")
class ProductCreatedListenerTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProductSnapshotRepository productSnapshotRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        productSnapshotRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 생성 이벤트를 받으면 상품 스냅샷을 저장하고 ProductSnapshotRegistered 를 남긴다")
    void registerSnapshot() {
        kafkaTemplate.send(productCreated(UUID.randomUUID(), 1L, "키보드", 30000));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(productSnapshotRepository.findByProductId(1L)).hasValueSatisfying(snapshot -> {
                    assertThat(snapshot.getName()).isEqualTo("키보드");
                    assertThat(snapshot.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(30000));
                }));
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getTopic()).isEqualTo("order.events");
            assertThat(message.getMessageKey()).isEqualTo("1");
            assertThat(message.getMessageType()).isEqualTo("ProductSnapshotRegistered");
            assertThat(message.getPayload()).isEqualTo("{\"productId\":1,\"name\":\"키보드\",\"price\":30000}");
        });
    }

    @Test
    @DisplayName("같은 이벤트가 다시 와도 스냅샷과 이벤트는 한 번만 만든다")
    void redelivered() {
        UUID messageId = UUID.randomUUID();
        kafkaTemplate.send(productCreated(messageId, 1L, "키보드", 30000));
        kafkaTemplate.send(productCreated(messageId, 1L, "키보드", 30000));
        kafkaTemplate.send(productCreated(UUID.randomUUID(), 2L, "마우스", 15000));

        // 같은 파티션이라 마우스가 처리됐으면 재전송된 키보드도 처리된 것이다
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(productSnapshotRepository.findByProductId(2L)).isPresent());
        assertThat(productSnapshotRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.findAll())
                .extracting(message -> message.getMessageKey())
                .containsExactly("1", "2");
    }

    private ProducerRecord<String, String> productCreated(UUID messageId, long productId, String name, long price) {
        ProducerRecord<String, String> record = new ProducerRecord<>("product.events", String.valueOf(productId),
                "{\"productId\":%d,\"name\":\"%s\",\"price\":%d}".formatted(productId, name, price));
        record.headers().add(MessageHeader.MESSAGE_ID, bytes(messageId.toString()));
        record.headers().add(MessageHeader.MESSAGE_TYPE, bytes("ProductCreated"));
        record.headers().add(MessageHeader.OCCURRED_AT, bytes(Instant.now().toString()));
        return record;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
