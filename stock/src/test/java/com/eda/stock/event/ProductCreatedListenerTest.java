package com.eda.stock.event;

import static com.eda.stock.support.ProductEvents.productCreated;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxRepository;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

class ProductCreatedListenerTest extends IntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        stockRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("상품 생성 이벤트를 받으면 재고 0 을 DB 와 Redis 에 저장하고 StockRegistered 를 남긴다")
    void registerStock() {
        kafkaTemplate.send(productCreated(UUID.randomUUID(), new ProductCreated(1L, "키보드", 30000)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("0"));
        assertThat(stockRepository.findByProductId(1L)).hasValueSatisfying(stock ->
                assertThat(stock.getQuantity()).isZero());
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getTopic()).isEqualTo("stock.events");
            assertThat(message.getMessageKey()).isEqualTo("1");
            assertThat(message.getMessageType()).isEqualTo("StockRegistered");
            assertThat(message.getPayload()).isEqualTo("{\"productId\":1,\"quantity\":0}");
        });
    }

    @Test
    @DisplayName("같은 이벤트가 다시 와도 재고 행과 이벤트를 다시 만들지 않고, 그사이 늘어난 Redis 재고를 덮어쓰지 않는다")
    void redelivered() {
        UUID messageId = UUID.randomUUID();
        ProductCreated keyboard = new ProductCreated(1L, "키보드", 30000);
        kafkaTemplate.send(productCreated(messageId, keyboard));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("0"));
        redis.opsForValue().set("stock:available:1", "100");

        kafkaTemplate.send(productCreated(messageId, keyboard));
        kafkaTemplate.send(productCreated(UUID.randomUUID(), new ProductCreated(2L, "마우스", 15000)));

        // 같은 파티션이라 마우스가 처리됐으면 재전송된 키보드도 처리된 것이다
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(redis.opsForValue().get("stock:available:2")).isEqualTo("0"));
        assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("100");
        assertThat(stockRepository.count()).isEqualTo(2);
        assertThat(outboxRepository.findAll())
                .extracting(message -> message.getMessageKey())
                .containsExactly("1", "2");
    }
}
