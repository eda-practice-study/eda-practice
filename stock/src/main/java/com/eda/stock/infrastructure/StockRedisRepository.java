package com.eda.stock.infrastructure;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

// stock:{productId} Hash = { available: 판매 가능 수량, reserved: 예약 중 수량 }
@Repository
@RequiredArgsConstructor
public class StockRedisRepository {

    private static final String KEY_PREFIX = "stock:";

    private static final RedisScript<Long> INITIALIZE_STOCK =
            RedisScript.of(new ClassPathResource("redis/initialize-stock.lua"), Long.class);

    private static final RedisScript<Long> INCREASE_AVAILABLE =
            RedisScript.of(new ClassPathResource("redis/increase-available.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;

    public void initialize(Long productId, int quantity) {
        redisTemplate.execute(
                INITIALIZE_STOCK,
                List.of(key(productId)),
                String.valueOf(quantity)
        );
    }

    public Long increaseAvailable(Long productId, int quantity) {
        return redisTemplate.execute(
                INCREASE_AVAILABLE,
                List.of(key(productId)),
                String.valueOf(quantity)
        );
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }
}
