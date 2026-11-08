package com.eda.stock.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StockTest {

    @Test
    @DisplayName("재고는 0 으로 생성된다")
    void createFor_shouldStartEmpty_whenProductProvided() {
        Stock stock = Stock.createFor(1L);

        assertThat(stock.getProductId()).isEqualTo(1L);
        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("상품 ID 없이 생성하면 실패한다")
    void createFor_shouldReject_whenProductIdMissing() {
        assertThatThrownBy(() -> Stock.createFor(null))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("재고를 추가하면 수량이 증가한다")
    void increase_shouldAddQuantity_whenQuantityPositive() {
        Stock stock = Stock.createFor(1L);

        stock.increase(10);

        assertThat(stock.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고를 차감하면 수량이 감소한다")
    void decrease_shouldSubtractQuantity_whenStockSufficient() {
        Stock stock = Stock.createFor(1L);
        stock.increase(10);

        stock.decrease(3);

        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("실재고보다 많이 확정하면 실패한다 (정합성 깨짐)")
    void decrease_shouldReject_whenStockInsufficient() {
        // given
        Stock stock = Stock.createFor(1L);
        stock.increase(2);

        // when & then
        assertThatThrownBy(() -> stock.decrease(3))
                .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
        assertThat(stock.getQuantity()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("추가할 수량이 0 이하이면 실패한다")
    void increase_shouldReject_whenQuantityNonPositive(int quantity) {
        Stock stock = Stock.createFor(1L);

        assertThatThrownBy(() -> stock.increase(quantity))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_QUANTITY);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    @DisplayName("차감할 수량이 0 이하이면 실패한다")
    void decrease_shouldReject_whenQuantityNonPositive(int quantity) {
        Stock stock = Stock.createFor(1L);

        assertThatThrownBy(() -> stock.decrease(quantity))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_QUANTITY);
    }
}
