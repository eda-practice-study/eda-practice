package com.eda.stock.infra;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class StockCacheIncreaseTest {
    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StockCache cache = new StockCache(template);

    @Test
    @DisplayName("Redis에 증가량을 더하고 증가 후 수량을 반환한다")
    void increase_shouldReturnAvailability_whenRedisSucceeds() {
        when(template.opsForValue()).thenReturn(values);
        when(values.increment("stock:available:1", 10)).thenReturn(110L);

        Long available = cache.increase(1L, 10);

        assertThat(available).isEqualTo(110L);
        verify(values, times(1)).increment("stock:available:1", 10);
    }

    @Test
    @DisplayName("Redis 호출이 실패하면 재시도하지 않고 null을 반환한다")
    void increase_shouldReturnNull_whenRedisFails() {
        when(template.opsForValue()).thenReturn(values);
        when(values.increment("stock:available:1", 10)).thenThrow(new RedisConnectionFailureException("unavailable"));

        Long available = cache.increase(1L, 10);

        assertThat(available).isNull();
        verify(values, times(1)).increment("stock:available:1", 10);
    }
}
