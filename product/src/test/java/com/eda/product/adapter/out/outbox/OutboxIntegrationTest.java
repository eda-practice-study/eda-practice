package com.eda.product.adapter.out.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.eda.common.event.EventTopics;
import com.eda.product.adapter.out.persistence.outbox.OutboxEventJpaRepository;
import com.eda.product.application.port.in.RegisterProductUseCase;
import com.eda.product.application.port.in.RegisterProductUseCase.RegisterProductCommand;
import com.eda.product.domain.outbox.OutboxEvent;
import com.eda.product.domain.outbox.OutboxStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

/**
 * 상품 생성 → outbox 적재 → 릴레이 발행 → Kafka 수신까지 이어서 확인한다.
 */
@SpringBootTest
@EmbeddedKafka(topics = EventTopics.PRODUCT_EVENTS, partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.relay.initial-delay=0",
        "outbox.relay.fixed-delay=200"
})
class OutboxIntegrationTest {

    @Autowired
    private RegisterProductUseCase registerProductUseCase;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        outboxEventJpaRepository.deleteAll();

        // 토픽은 테스트 간에 공유된다. 테스트마다 다른 컨슈머 그룹으로 처음부터 읽고 키로 골라낸다
        Map<String, Object> config = KafkaTestUtils.consumerProps(
                embeddedKafkaBroker.getBrokersAsString(),
                testInfo.getTestMethod().orElseThrow().getName(),
                "true");
        consumer = new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, EventTopics.PRODUCT_EVENTS);
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    @DisplayName("상품을 생성하면 outbox에 적재되고 릴레이가 Kafka로 발행한다")
    void recordThenRelayToKafka() {
        // when
        Long productId = registerProductUseCase.register(
                new RegisterProductCommand("티셔츠", BigDecimal.valueOf(10000)));

        // then: 릴레이가 발행 완료로 바꾼다
        awaitPublished();

        // then: 메시지가 실제로 Kafka 에 도착한다
        assertThat(receivedValueOf(productId)).contains("\"productId\":" + productId);
    }

    @Test
    @DisplayName("발행된 메시지는 outbox에 저장된 payload와 정확히 같다")
    void sendStoredPayloadAsIs() {
        // when
        Long productId = registerProductUseCase.register(
                new RegisterProductCommand("청바지", BigDecimal.valueOf(39000)));
        awaitPublished();

        // then: 기록으로 남은 payload 가 곧 발행된 내용이다
        OutboxEvent stored = outboxEventJpaRepository.findAll().getFirst();
        assertThat(receivedValueOf(productId)).isEqualTo(stored.getPayload());
    }

    @Test
    @DisplayName("발행 완료된 outbox 레코드는 삭제하지 않고 남긴다")
    void keepPublishedRecord() {
        // when
        registerProductUseCase.register(new RegisterProductCommand("티셔츠", BigDecimal.valueOf(10000)));
        awaitPublished();

        // then: 발행 후에도 기록이 남는다
        assertThat(outboxEventJpaRepository.count()).isOne();
    }

    private void awaitPublished() {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(outboxEventJpaRepository.findAll()).singleElement()
                        .satisfies(event -> {
                            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
                            assertThat(event.getPublishedAt()).isNotNull();
                        }));
    }

    /**
     * 메시지 키가 productId 인 레코드의 값. 앞선 테스트가 남긴 메시지와 섞이지 않도록 키로 골라낸다.
     */
    private String receivedValueOf(Long productId) {
        String key = String.valueOf(productId);

        return StreamSupport.stream(
                        KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(20))
                                .records(EventTopics.PRODUCT_EVENTS).spliterator(), false)
                .filter(record -> key.equals(record.key()))
                .map(ConsumerRecord::value)
                .findFirst()
                .orElseThrow(() -> new AssertionError("키가 %s 인 메시지를 받지 못했습니다".formatted(key)));
    }
}
