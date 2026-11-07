package com.eda.stock.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockRedisRepository {

    private final StringRedisTemplate redisTemplate;

    /**
     * 최초 등록에만 재고 0을 저장한다. 기존 가용 재고는 덮어쓰지 않는다.
     * Redis 접속 실패는 호출자에게 예외로 전달한다.
     */
    public void initialize(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("productId는 필수입니다");
        }

        String key = "stock:" + productId + ":available";
        redisTemplate.opsForValue().setIfAbsent(key, "0");
    }
}
