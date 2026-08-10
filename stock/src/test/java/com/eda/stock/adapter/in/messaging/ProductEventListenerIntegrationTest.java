package com.eda.stock.adapter.in.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.eda.common.event.EventTopics;
import com.eda.common.event.ProductCreatedEvent;
import com.eda.stock.adapter.out.persistence.StockJpaRepository;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EmbeddedKafka(topics = EventTopics.PRODUCT_EVENTS, partitions = 1)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JacksonJsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
        "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JacksonJsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.eda.common.event",
        "spring.kafka.consumer.properties.spring.json.value.default.type=com.eda.common.event.ProductCreatedEvent",
        "spring.kafka.consumer.properties.spring.json.use.type.headers=false"
})
class ProductEventListenerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @Test
    @DisplayName("발행된 상품 생성 이벤트를 소비해 수량 0인 재고를 만든다")
    void consumeProductCreatedEventAndCreateStock() {
        // given
        ProductCreatedEvent event = ProductCreatedEvent.of(1001L, "티셔츠", BigDecimal.valueOf(10000));

        // when
        kafkaTemplate.send(EventTopics.PRODUCT_EVENTS, String.valueOf(event.productId()), event);

        // then
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stockJpaRepository.findByProductId(1001L))
                        .isPresent()
                        .get()
                        .satisfies(stock -> assertThat(stock.getQuantity()).isZero()));
    }

    @Test
    @DisplayName("같은 이벤트를 두 번 발행해도 재고는 하나만 생성된다")
    void consumeDuplicatedEventIdempotently() {
        // given
        ProductCreatedEvent event = ProductCreatedEvent.of(1002L, "청바지", BigDecimal.valueOf(39000));

        // when: 같은 이벤트가 중복 전달되는 상황
        kafkaTemplate.send(EventTopics.PRODUCT_EVENTS, String.valueOf(event.productId()), event);
        kafkaTemplate.send(EventTopics.PRODUCT_EVENTS, String.valueOf(event.productId()), event);

        // then
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stockJpaRepository.findByProductId(1002L)).isPresent());

        assertThat(stockJpaRepository.findAll().stream()
                .filter(stock -> stock.getProductId().equals(1002L))
                .count()).isEqualTo(1);
    }
}
