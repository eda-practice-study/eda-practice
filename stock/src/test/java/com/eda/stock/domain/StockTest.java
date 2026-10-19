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
    void createFor() {
        Stock stock = Stock.createFor(1L);

        assertThat(stock.getProductId()).isEqualTo(1L);
        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("상품 ID 없이 생성하면 실패한다")
    void failToCreateWithoutProductId() {
        assertThatThrownBy(() -> Stock.createFor(null))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("입고하면 늘고, 확정하면 줄어든다")
    void increaseAndDecrease() {
        // given
        Stock stock = Stock.createFor(1L);

        // when
        stock.increase(10);
        stock.decrease(3);

        // then
        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("실재고보다 많이 확정하면 실패한다 (정합성 깨짐)")
    void failToDecreaseMoreThanQuantity() {
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
    @DisplayName("0 이하 수량으로는 증감할 수 없다")
    void failWithNonPositiveQuantity(int quantity) {
        Stock stock = Stock.createFor(1L);

        assertThatThrownBy(() -> stock.increase(quantity))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_QUANTITY);
        assertThatThrownBy(() -> stock.decrease(quantity))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_QUANTITY);
    }
}
