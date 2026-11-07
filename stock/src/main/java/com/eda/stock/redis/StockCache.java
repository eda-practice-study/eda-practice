package com.eda.stock.redis;

import com.eda.stock.event.StockSnapshot;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockCache {

    private static final String TTL_SECONDS = "60";
    private static final DefaultRedisScript<Long> APPLY_SNAPSHOT = new DefaultRedisScript<>();

    static {
        APPLY_SNAPSHOT.setLocation(new ClassPathResource("redis/apply-stock-snapshot.lua"));
        APPLY_SNAPSHOT.setResultType(Long.class);
    }

    private final StringRedisTemplate redis;

    public void apply(StockSnapshot snapshot) {
        redis.execute(APPLY_SNAPSHOT, List.of(key(snapshot.productId())),
                Long.toString(snapshot.version()), Integer.toString(snapshot.quantity()), TTL_SECONDS);
    }

    public StockSnapshot find(Long productId) {
        String value = redis.opsForValue().get(key(productId));
        if (value == null) {
            return null;
        }
        String[] parts = value.split(":", 2);
        return new StockSnapshot(productId, Integer.parseInt(parts[1]), Long.parseLong(parts[0]));
    }

    private String key(Long productId) {
        return "stock:snapshot:" + productId;
    }
}
