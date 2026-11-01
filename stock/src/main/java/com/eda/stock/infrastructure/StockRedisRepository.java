package com.eda.stock.infrastructure;

import java.util.List;
import java.util.Optional;
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

    private static final RedisScript<Long> RESYNC_STOCK =
            RedisScript.of(new ClassPathResource("redis/resync-stock.lua"), Long.class);

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

    public Optional<RedisStock> find(Long productId) {
        List<Object> values = redisTemplate.opsForHash().multiGet(key(productId), List.of("available", "reserved"));
        if (values.get(0) == null) {
            return Optional.empty();
        }

        long available = Long.parseLong((String) values.get(0));
        long reserved = values.get(1) == null ? 0 : Long.parseLong((String) values.get(1));
        return Optional.of(new RedisStock(available, reserved));
    }

    public boolean resync(Long productId, int quantity) {
        Long result = redisTemplate.execute(
                RESYNC_STOCK,
                List.of(key(productId)),
                String.valueOf(quantity)
        );
        return Long.valueOf(1).equals(result);
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }
}
