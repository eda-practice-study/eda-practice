package com.eda.stock.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockRedisRepository {
    private final StringRedisTemplate redisTemplate;

    public void initializeIfAbsent(Long productId, int quantity) {
        String key = "stock:available:" + productId;

        // redisTemplate은 redis 접근 도구, opsForValue는 Redis의 문자열 값을 다루는 기능을 가져오는 메서드
        redisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(quantity));
    }

    public Long increase(Long productId, int quantity) {
        String key = "stock:available:" + productId;

        return redisTemplate.opsForValue()
                .increment(key, quantity);
    }
}
