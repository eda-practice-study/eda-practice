package com.eda.stock.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.api.StockIncreaseResponse;
import com.eda.stock.infra.StockCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class StockIncreaseServiceTest {
    @Mock StockWriteService stockWriteService;
    @Mock StockCache stockCache;
    @InjectMocks StockIncreaseService service;

    @Test
    @DisplayName("DB 증가가 끝난 뒤 Redis를 증가시키고 수량을 반환한다")
    void increaseForRequest_shouldReturnStock_whenDatabaseCommitted() {
        // given
        when(stockWriteService.increase("key", 1L, 10)).thenReturn(110);
        when(stockCache.increase(1L, 10)).thenReturn(110L);

        // when
        StockIncreaseResponse response = service.increaseForRequest("key", 1L, 10);

        // then
        assertThat(response).isEqualTo(new StockIncreaseResponse(1L, 110, 110L));
        InOrder order = inOrder(stockWriteService, stockCache);
        order.verify(stockWriteService).increase("key", 1L, 10);
        order.verify(stockCache).increase(1L, 10);
        verifyNoMoreInteractions(stockWriteService, stockCache);
    }

    @Test
    @DisplayName("Redis 결과가 불확실하면 DB 수량과 가용 재고 null을 반환한다")
    void increaseForRequest_shouldReturnNullAvailability_whenRedisFails() {
        // given
        when(stockWriteService.increase("key", 1L, 10)).thenReturn(110);
        when(stockCache.increase(1L, 10)).thenReturn(null);

        // when
        StockIncreaseResponse response = service.increaseForRequest("key", 1L, 10);

        // then
        assertThat(response).isEqualTo(new StockIncreaseResponse(1L, 110, null));
        verify(stockCache, times(1)).increase(1L, 10);
    }

    @Test
    @DisplayName("멱등성 키 저장이 중복으로 실패하면 같은 예외를 던지고 Redis를 호출하지 않는다")
    void increaseForRequest_shouldThrowSameException_whenKeySaveConflicts() {
        // given
        var failure = new DataIntegrityViolationException("duplicate key");
        when(stockWriteService.increase("key", 1L, 10)).thenThrow(failure);

        // when, then
        assertThatThrownBy(() -> service.increaseForRequest("key", 1L, 10)).isSameAs(failure);
        verify(stockWriteService, times(1)).increase("key", 1L, 10);
        verifyNoInteractions(stockCache);
    }

    @Test
    @DisplayName("낙관적 락 충돌은 같은 예외를 던지고 재시도하거나 Redis를 호출하지 않는다")
    void increaseForRequest_shouldThrowSameException_whenOptimisticLockConflicts() {
        // given
        var failure = new ObjectOptimisticLockingFailureException("Stock", 1L);
        when(stockWriteService.increase("key", 1L, 10)).thenThrow(failure);

        // when, then
        assertThatThrownBy(() -> service.increaseForRequest("key", 1L, 10)).isSameAs(failure);
        verify(stockWriteService, times(1)).increase("key", 1L, 10);
        verifyNoInteractions(stockCache);
    }

    @Test
    @DisplayName("멱등성 키가 공백이면 검증 예외를 던진다")
    void increaseForRequest_shouldThrowValidationException_whenKeyBlank() {
        // when, then
        assertThatThrownBy(() -> service.increaseForRequest(" ", 1L, 10))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(stockWriteService, stockCache);
    }
}
