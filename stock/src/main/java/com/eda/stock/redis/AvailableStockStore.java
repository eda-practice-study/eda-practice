package com.eda.stock.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class AvailableStockStore {

    private static final String KEY_PREFIX = "stock:available:";
    private static final String ORDER_KEY_PREFIX = "stock:order:";
    private static final String RESTORED = "RESTORED";

    // 키가 없을 때 INCRBY 를 하면 0 부터 시작해 DB 와 다른 값이 생기므로, 키가 있을 때만 더한다
    private static final RedisScript<Long> INCREASE_IF_PRESENT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return redis.call('INCRBY', KEYS[1], ARGV[1])
            end
            return nil
            """, Long.class);

    // KEYS[1] 은 주문 기록, KEYS[2..] 는 상품별 가용 재고. ARGV 는 TTL, 기록할 차감 내역, 상품별 수량 순이다.
    private static final RedisScript<Long> DECREASE_ALL = RedisScript.of("""
            local recorded = redis.call('GET', KEYS[1])
            if recorded == 'RESTORED' then
                return -1
            end
            if recorded then
                return 1
            end
            for i = 2, #KEYS do
                local available = redis.call('GET', KEYS[i])
                if not available or tonumber(available) < tonumber(ARGV[i + 1]) then
                    return 0
                end
            end
            for i = 2, #KEYS do
                redis.call('DECRBY', KEYS[i], ARGV[i + 1])
            end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[1])
            return 1
            """, Long.class);

    // 차감 기록이 없을 때도 취소 표시를 남긴다. 늦게 도착한 차감이 그 표시를 보고 거부한다.
    private static final RedisScript<Long> RESTORE = RedisScript.of("""
            local recorded = redis.call('GET', KEYS[1])
            if recorded == 'RESTORED' then
                return 0
            end
            if recorded then
                for productId, quantity in string.gmatch(recorded, '(%d+):(%d+)') do
                    local key = ARGV[2] .. productId
                    if redis.call('EXISTS', key) == 1 then
                        redis.call('INCRBY', key, quantity)
                    end
                end
            end
            redis.call('SET', KEYS[1], 'RESTORED', 'EX', ARGV[1])
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Duration orderRecordTtl;

    public AvailableStockStore(
            StringRedisTemplate redis, @Value("${stock.order-record.ttl:1h}") Duration orderRecordTtl) {
        this.redis = redis;
        this.orderRecordTtl = orderRecordTtl;
    }

    public enum DecreaseResult {
        DECREASED, OUT_OF_STOCK, CANCELLED
    }

    // 키가 이미 있으면 덮어쓰지 않는다. 재전송된 이벤트가 그사이 늘어난 재고를 지우지 않게 한다.
    public void initialize(Long productId, int quantity) {
        redis.opsForValue().setIfAbsent(key(productId), String.valueOf(quantity));
    }

    public Optional<Long> increaseIfPresent(Long productId, int quantity) {
        return Optional.ofNullable(
                redis.execute(INCREASE_IF_PRESENT, List.of(key(productId)), String.valueOf(quantity)));
    }

    public DecreaseResult decreaseAll(String orderKey, Map<Long, Integer> quantities) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();
        keys.add(orderKey(orderKey));
        args.add(String.valueOf(orderRecordTtl.toSeconds()));
        args.add(quantities.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(",")));
        quantities.forEach((productId, quantity) -> {
            keys.add(key(productId));
            args.add(String.valueOf(quantity));
        });

        long result = redis.execute(DECREASE_ALL, keys, args.toArray());
        if (result == 1) {
            return DecreaseResult.DECREASED;
        }
        return result == 0 ? DecreaseResult.OUT_OF_STOCK : DecreaseResult.CANCELLED;
    }

    public void restore(String orderKey) {
        redis.execute(RESTORE, List.of(orderKey(orderKey)), String.valueOf(orderRecordTtl.toSeconds()), KEY_PREFIX);
    }

    private String key(Long productId) {
        return KEY_PREFIX + productId;
    }

    private String orderKey(String orderKey) {
        return ORDER_KEY_PREFIX + orderKey;
    }
}
