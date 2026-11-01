package com.eda.stock.event;

import static com.eda.stock.support.ProductEvents.productCreated;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxRepository;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.redis.AvailableStockStore;
import com.eda.stock.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class ProductCreatedRedisFailureTest extends IntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private StringRedisTemplate redis;

    @MockitoSpyBean
    private AvailableStockStore availableStockStore;

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        stockRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("Redis 가 한 번 실패하면 재시도로 Redis 까지 채우고, 재고 행과 이벤트는 한 번만 만든다")
    void retryAfterRedisFailure() {
        doThrow(new RedisConnectionFailureException("Redis 장애"))
                .doCallRealMethod()
                .when(availableStockStore).initialize(anyLong(), anyInt());

        kafkaTemplate.send(productCreated(UUID.randomUUID(), new ProductCreated(1L, "키보드", 30000)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("0"));
        assertThat(stockRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis 가 계속 실패해도 재고 행과 StockRegistered 는 남는다")
    void keepStockWhenRedisKeepsFailing() {
        doThrow(new RedisConnectionFailureException("Redis 장애"))
                .when(availableStockStore).initialize(anyLong(), anyInt());

        kafkaTemplate.send(productCreated(UUID.randomUUID(), new ProductCreated(1L, "키보드", 30000)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(stockRepository.findByProductId(1L)).isPresent());
        assertThat(outboxRepository.count()).isEqualTo(1);
        assertThat(redis.opsForValue().get("stock:available:1")).isNull();
    }
}
