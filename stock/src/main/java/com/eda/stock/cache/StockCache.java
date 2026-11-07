package com.eda.stock.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockCache {

    private final StringRedisTemplate redisTemplate;

    public void initialize(Long productId) {
        try {
            redisTemplate.opsForValue().setIfAbsent("stock:available:" + productId, "0");
        } catch (DataAccessException ex) {
            log.warn("Redis 초기 재고 저장 실패 productId={}", productId, ex);
        }
    }
}
