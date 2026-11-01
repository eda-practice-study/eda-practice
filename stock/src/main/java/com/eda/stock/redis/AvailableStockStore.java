package com.eda.stock.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// Redis 의 가용 재고. DB 재고(실재고)가 원본이고 여기는 따라 움직인다.
@Component
@RequiredArgsConstructor
public class AvailableStockStore {

    private static final String KEY_PREFIX = "stock:available:";

    private final StringRedisTemplate redis;

    // 키가 이미 있으면 덮어쓰지 않는다. 재전송된 이벤트가 그사이 늘어난 재고를 지우지 않게 한다.
    public void initialize(Long productId, int quantity) {
        redis.opsForValue().setIfAbsent(key(productId), String.valueOf(quantity));
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }
}
