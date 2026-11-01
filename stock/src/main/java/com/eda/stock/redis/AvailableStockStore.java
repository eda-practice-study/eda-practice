package com.eda.stock.redis;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvailableStockStore {

    private static final String KEY_PREFIX = "stock:available:";

    // 키가 없을 때 INCRBY 를 하면 0 부터 시작해 DB 와 다른 값이 생기므로, 키가 있을 때만 더한다
    private static final RedisScript<Long> INCREASE_IF_PRESENT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return redis.call('INCRBY', KEYS[1], ARGV[1])
            end
            return nil
            """, Long.class);

    private final StringRedisTemplate redis;

    // 키가 이미 있으면 덮어쓰지 않는다. 재전송된 이벤트가 그사이 늘어난 재고를 지우지 않게 한다.
    public void initialize(Long productId, int quantity) {
        redis.opsForValue().setIfAbsent(key(productId), String.valueOf(quantity));
    }

    public Optional<Long> increaseIfPresent(Long productId, int quantity) {
        return Optional.ofNullable(
                redis.execute(INCREASE_IF_PRESENT, List.of(key(productId)), String.valueOf(quantity)));
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }
}
