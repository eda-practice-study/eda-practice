package com.eda.stock.infra;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class StockCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> values;
    @InjectMocks
    private StockCache stockCache;

    @Test
    @DisplayName("새 상품의 Redis 수량을 덮어쓰기 없이 0으로 초기화한다")
    void initialize() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("stock:available:1", "0")).thenReturn(true);

        stockCache.initialize(1L);

        verify(values).setIfAbsent("stock:available:1", "0");
        verifyNoMoreInteractions(values);
    }

    @Test
    @DisplayName("기존 키로 초기화가 거절되어도 값을 덮어쓰지 않는다")
    void existingKey() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("stock:available:1", "0")).thenReturn(false);

        assertThatCode(() -> stockCache.initialize(1L)).doesNotThrowAnyException();

        verify(values).setIfAbsent("stock:available:1", "0");
        verifyNoMoreInteractions(values);
    }

    @Test
    @DisplayName("Redis 연결 실패는 리스너로 전파하지 않는다")
    void connectionFailure() {
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("stock:available:1", "0"))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

        assertThatCode(() -> stockCache.initialize(1L)).doesNotThrowAnyException();

        verify(values, times(1)).setIfAbsent("stock:available:1", "0");
    }
}
