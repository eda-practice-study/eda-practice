package com.eda.product.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductTest {

    @Test
    @DisplayName("상품을 등록하면 ACTIVE 상태로 생성된다")
    void registerProductAsActive() {
        // when
        Product product = Product.register("티셔츠", BigDecimal.valueOf(10000));

        // then
        assertThat(product.getName()).isEqualTo("티셔츠");
        assertThat(product.getPrice()).isEqualByComparingTo("10000");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @ParameterizedTest
    @MethodSource("invalidRegisterArgs")
    @DisplayName("이름이나 가격이 유효하지 않으면 등록에 실패한다")
    void failToRegisterWithInvalidInput(String name, BigDecimal price) {
        // when & then
        assertThatThrownBy(() -> Product.register(name, price))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    static Stream<Arguments> invalidRegisterArgs() {
        BigDecimal price = BigDecimal.valueOf(10000);
        return Stream.of(
                Arguments.of(null, price),                     // 이름 null
                Arguments.of("  ", price),                     // 이름 blank
                Arguments.of("티셔츠", null),                    // 가격 null
                Arguments.of("티셔츠", BigDecimal.ZERO),          // 가격 0
                Arguments.of("티셔츠", BigDecimal.valueOf(-1))    // 가격 음수
        );
    }

    @Test
    @DisplayName("상품을 비활성화하면 DEACTIVATED 상태가 된다")
    void deactivateProduct() {
        // given
        Product product = Product.register("티셔츠", BigDecimal.valueOf(10000));

        // when
        product.deactivate();

        // then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DEACTIVATED);
    }
}
