package com.eda.product.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductTest {

    @Test
    @DisplayName("상품을 등록한다")
    void register_shouldCreateProduct_whenInputValid() {
        // when
        Product product = Product.register("키보드", BigDecimal.valueOf(30000));

        // then
        assertThat(product.getName()).isEqualTo("키보드");
        assertThat(product.getPrice()).isEqualByComparingTo("30000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1000000", "30000.00"})
    @DisplayName("가격은 원 단위 정수 1 ~ 1,000,000 이다")
    void register_shouldSucceed_whenPriceValid(String price) {
        assertThatCode(() -> Product.register("키보드", new BigDecimal(price))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1000001", "100.5"})
    @DisplayName("가격이 범위를 벗어나거나 소수면 등록에 실패한다")
    void register_shouldReject_whenPriceInvalid(String price) {
        assertThatThrownBy(() -> Product.register("키보드", new BigDecimal(price)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("가격이 없으면 등록에 실패한다")
    void register_shouldReject_whenPriceMissing() {
        assertThatThrownBy(() -> Product.register("키보드", null))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("상품명이 비어 있으면 등록에 실패한다")
    void register_shouldReject_whenNameBlank(String name) {
        assertThatThrownBy(() -> Product.register(name, BigDecimal.TEN))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("상품명이 100자이면 등록할 수 있다")
    void register_shouldSucceed_whenNameAtLimit() {
        assertThatCode(() -> Product.register("가".repeat(100), BigDecimal.TEN)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("상품명이 100자를 넘으면 등록에 실패한다")
    void register_shouldFail_whenNameTooLong() {
        assertThatThrownBy(() -> Product.register("가".repeat(101), BigDecimal.TEN))
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
