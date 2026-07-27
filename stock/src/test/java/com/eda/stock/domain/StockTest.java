package com.eda.stock.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StockTest {

    @Test
    @DisplayName("재고를 생성하면 수량은 0이다")
    void createStockWithZeroQuantity() {
        // when
        Stock stock = Stock.createFor(1L);

        // then
        assertThat(stock.getProductId()).isEqualTo(1L);
        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("상품 ID 없이 재고를 생성하면 실패한다")
    void failToCreateStockForNullProduct() {
        // when & then
        assertThatThrownBy(() -> Stock.createFor(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("재고를 등록하면 수량이 증가한다")
    void addStock() {
        // given
        Stock stock = Stock.createFor(1L);

        // when
        stock.add(10);

        // then
        assertThat(stock.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("재고를 차감하면 수량이 감소한다")
    void deductStock() {
        // given
        Stock stock = Stock.createFor(1L);
        stock.add(10);

        // when
        stock.deduct(3);

        // then
        assertThat(stock.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("보유 수량보다 많이 차감하면 실패한다")
    void failToDeductMoreThanQuantity() {
        // given
        Stock stock = Stock.createFor(1L);
        stock.add(3);

        // when & then
        assertThatThrownBy(() -> stock.deduct(5))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
    }

    @Test
    @DisplayName("재고를 복원하면 수량이 증가한다")
    void restoreStock() {
        // given
        Stock stock = Stock.createFor(1L);
        stock.add(5);
        stock.deduct(5);

        // when
        stock.restore(2);

        // then
        assertThat(stock.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("0 이하 수량으로 처리하면 실패한다")
    void failWhenQuantityIsNotPositive() {
        // given
        Stock stock = Stock.createFor(1L);

        // when & then
        assertThatThrownBy(() -> stock.add(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_OPERATION);
        assertThatThrownBy(() -> stock.deduct(-1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_OPERATION);
        assertThatThrownBy(() -> stock.restore(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_OPERATION);
    }
}
