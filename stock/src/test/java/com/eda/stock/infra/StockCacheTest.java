package com.eda.stock.infra;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class StockCacheTest {
    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    private final StockCache cache = new StockCache(template);

    @Test
    @DisplayName("Redis 초기화 실패를 호출자에게 전파하지 않는다")
    @SuppressWarnings("unchecked")
    void initialize_shouldReturnNormally_whenRedisFails() {
        // given
        doThrow(new RedisConnectionFailureException("unavailable")).when(template)
                .execute(any(RedisScript.class), eq(List.of("stock:available:1")));

        // when, then
        assertThatCode(() -> cache.initialize(1L)).doesNotThrowAnyException();
    }
}
