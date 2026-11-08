package com.eda.stock.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StockRedisRepository {

    private final StringRedisTemplate redisTemplate;

    // 존재 확인과 증가를 Redis에서 원자적으로 실행한다.
    // INCRBY만 사용하면 키가 없을 때 증가량으로 새 키가 만들어지므로 이를 방지한다.
    private static final DefaultRedisScript<Long> INCREASE_IF_PRESENT =
            new DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return nil
                    end
                    return redis.call('INCRBY', KEYS[1], ARGV[1])
                    """, Long.class);

    /**
     * 기존 가용 재고에 증가량을 더하고 증가 후 값을 반환한다.
     * 키가 없으면 null, Redis 접속·명령 실패 시에는 예외를 전달한다.
     */
    public Long increase(Long productId, int quantity) {
        String key = "stock:" + productId + ":available";
        // KEYS[1]: 재고 키 / ARGV[1]: 문자열 증가량
        // 별도의 Redis 트랜잭션을 시작하지 않고 Lua 스크립트 하나로 처리한다.
        return redisTemplate.execute(INCREASE_IF_PRESENT, List.of(key), String.valueOf(quantity));
    }

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
