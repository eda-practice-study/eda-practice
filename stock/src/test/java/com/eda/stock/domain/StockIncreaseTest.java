package com.eda.stock.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockIncreaseTest {
    @Test
    @DisplayName("누적 재고가 int 범위를 넘으면 예외를 던지고 기존 수량을 유지한다")
    void increase_shouldThrowQuantityException_whenTotalOverflows() {
        Stock stock = Stock.createFor(1L);
        stock.increase(Integer.MAX_VALUE);

        assertThatThrownBy(() -> stock.increase(1))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STOCK_QUANTITY));
        assertThat(stock.getQuantity()).isEqualTo(Integer.MAX_VALUE);
    }
}
