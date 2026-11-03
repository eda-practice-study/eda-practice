package com.eda.stock.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StockRedisRepository {

    private static final RedisScript<Long> INCREASE_IF_PRESENT =
            // 레디스에 키존재확인 없다 -> 아무일없음, 있다 -> 그 키 값에 증가
            RedisScript.of("""
                    if redis.call('EXISTS', KEYS[1]) == 0 then
                        return nil
                    end
                    return redis.call('INCRBY', KEYS[1], ARGV[1])
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public void initializeIfAbsent(Long productId, int quantity) {
        String key = "stock:available:" + productId;

        // redisTemplate은 redis 접근 도구, opsForValue는 Redis의 문자열 값을 다루는 기능을 가져오는 메서드
        redisTemplate.opsForValue()
                .setIfAbsent(key, String.valueOf(quantity));
    }

    public Long increase(Long productId, int quantity) {
        String key = "stock:available:" + productId;


        /*
         * <키확인과 증가를 하나로 묶기 위해 lua script 활용>
         * Lua 스크립트도 Redis의 명령을 실행하는 메인 스레드에서 실행됩니다.
         * 실행 중에는 같은 Redis 인스턴스의 다른 클라이언트 명령이 끼어들지 못해요.
         */
        return redisTemplate.execute(
                INCREASE_IF_PRESENT,
                List.of(key),
                String.valueOf(quantity)
        );
    }
}
