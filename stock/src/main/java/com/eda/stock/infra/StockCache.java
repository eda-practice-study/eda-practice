package com.eda.stock.infra;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockCache {

    private static final RedisScript<Long> SYNCHRONIZE = RedisScript.of(
            new ClassPathResource("redis/synchronize-stock.lua"), Long.class);
    private static final RedisScript<Long> INITIALIZE = RedisScript.of(
            new ClassPathResource("redis/initialize-stock.lua"), Long.class);

    private final StringRedisTemplate redisTemplate;

    public Long synchronize(Long productId, int quantity, long version) {
        try {
            return redisTemplate.execute(SYNCHRONIZE, List.of("stock:available:" + productId),
                    Integer.toString(quantity), Long.toString(version));
        } catch (DataAccessException ex) {
            log.warn("Redis 재고 반영 실패 productId={} quantity={} version={}", productId, quantity, version, ex);
            return null;
        }
    }

    public void initialize(Long productId) {
        try {
            redisTemplate.execute(INITIALIZE, List.of("stock:available:" + productId));
        } catch (DataAccessException ex) {
            log.warn("Redis 초기 재고 저장 실패 productId={}", productId, ex);
        }
    }
}
