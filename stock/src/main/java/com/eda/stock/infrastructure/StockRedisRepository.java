package com.eda.stock.infrastructure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

// stock:{productId} Hash = { available: 판매 가능 수량, reserved: 예약 중 수량 }
// reservation:{reservationId} Hash = { status: RESERVED | CONFIRMED | CANCELLED, {productId}: 예약 수량 }
// reservations ZSet = 진행 중(RESERVED)인 예약 ID, score = 예약 시각 ms
@Repository
@RequiredArgsConstructor
public class StockRedisRepository {

    private static final String KEY_PREFIX = "stock:";
    private static final String RESERVATION_KEY_PREFIX = "reservation:";
    private static final String RESERVATIONS_KEY = "reservations";

    private static final RedisScript<Long> INITIALIZE_STOCK =
            RedisScript.of(new ClassPathResource("redis/initialize-stock.lua"), Long.class);

    private static final RedisScript<Long> INCREASE_AVAILABLE =
            RedisScript.of(new ClassPathResource("redis/increase-available.lua"), Long.class);

    private static final RedisScript<Long> RESERVE_STOCK =
            RedisScript.of(new ClassPathResource("redis/reserve-stock.lua"), Long.class);

    private static final RedisScript<Long> CONFIRM_RESERVATION =
            RedisScript.of(new ClassPathResource("redis/confirm-reservation.lua"), Long.class);

    private static final RedisScript<Long> CANCEL_RESERVATION =
            RedisScript.of(new ClassPathResource("redis/cancel-reservation.lua"), Long.class);

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

    // TCC Try. 결과는 reserve-stock.lua 의 반환값 그대로 (0 = 예약함, -1 = 취소된 ID, i > 0 = i 번째 상품 부족)
    public long reserve(UUID reservationId, Map<Long, Integer> quantities, long reservedAtMillis) {
        List<Long> productIds = List.copyOf(quantities.keySet());

        List<String> keys = new ArrayList<>();
        keys.add(reservationKey(reservationId));
        keys.add(RESERVATIONS_KEY);
        productIds.forEach(productId -> keys.add(key(productId)));

        List<String> args = new ArrayList<>();
        args.add(reservationId.toString());
        args.add(String.valueOf(reservedAtMillis));
        productIds.forEach(productId -> {
            args.add(String.valueOf(productId));
            args.add(String.valueOf(quantities.get(productId)));
        });

        return redisTemplate.execute(RESERVE_STOCK, keys, args.toArray());
    }

    // 1 = 확정함, 0 = 이미 확정함, -1 = 예약이 없거나 취소됨
    public long confirm(UUID reservationId, Duration recordTtl) {
        return executeReservation(CONFIRM_RESERVATION, reservationId, recordTtl);
    }

    // 1 = 취소함, 0 = 되돌릴 예약 없음 (취소 표식만 남김), -1 = 이미 확정됨
    public long cancel(UUID reservationId, Duration recordTtl) {
        return executeReservation(CANCEL_RESERVATION, reservationId, recordTtl);
    }

    // reservedBeforeMillis 이전에 예약된 채로 남아 있는 예약 ID
    public Set<String> findReservedBefore(long reservedBeforeMillis, int limit) {
        return redisTemplate.opsForZSet().rangeByScore(RESERVATIONS_KEY, 0, reservedBeforeMillis, 0, limit);
    }

    private long executeReservation(RedisScript<Long> script, UUID reservationId, Duration recordTtl) {
        return redisTemplate.execute(
                script,
                List.of(reservationKey(reservationId), RESERVATIONS_KEY),
                reservationId.toString(),
                String.valueOf(recordTtl.toSeconds())
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

    private String reservationKey(UUID reservationId) {
        return RESERVATION_KEY_PREFIX + reservationId;
    }
}
