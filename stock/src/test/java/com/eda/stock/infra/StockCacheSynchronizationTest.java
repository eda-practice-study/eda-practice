package com.eda.stock.infra;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class StockCacheSynchronizationTest {
    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    private final StockCache cache = new StockCache(template);

    @Test
    @DisplayName("수량과 버전을 스크립트에 전달하고 해당 버전으로 반영한 수량을 반환한다")
    @SuppressWarnings("unchecked")
    void synchronize_shouldReturnAvailability_whenRedisSucceeds() {
        // given
        when(template.execute(any(RedisScript.class), eq(List.of("stock:available:1")), eq("110"), eq("5")))
                .thenReturn(110L);

        // when
        Long available = cache.synchronize(1L, 110, 5L);

        // then
        assertThat(available).isEqualTo(110L);
    }

    @Test
    @DisplayName("Redis에 더 최신 버전이 있으면 해당 요청의 가용 재고는 null로 반환한다")
    @SuppressWarnings("unchecked")
    void synchronize_shouldReturnNull_whenVersionSuperseded() {
        // given
        when(template.execute(any(RedisScript.class), eq(List.of("stock:available:1")), eq("110"), eq("5")))
                .thenReturn(null);

        // when
        Long available = cache.synchronize(1L, 110, 5L);

        // then
        assertThat(available).isNull();
        verify(template, times(1)).execute(any(RedisScript.class), eq(List.of("stock:available:1")), eq("110"), eq("5"));
    }

    @Test
    @DisplayName("Redis 반영에 실패하면 직접 재시도하지 않고 null을 반환한다")
    @SuppressWarnings("unchecked")
    void synchronize_shouldReturnNull_whenRedisFails() {
        // given
        when(template.execute(any(RedisScript.class), eq(List.of("stock:available:1")), eq("110"), eq("5")))
                .thenThrow(new RedisConnectionFailureException("unavailable"));

        // when
        Long available = cache.synchronize(1L, 110, 5L);

        // then
        assertThat(available).isNull();
        verify(template, times(1)).execute(any(RedisScript.class), eq(List.of("stock:available:1")), eq("110"), eq("5"));
    }
}
