package com.eda.order.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderTest {

    private Order.LineItem lineItem(long productId, int quantity) {
        return new Order.LineItem(productId, "상품" + productId, BigDecimal.valueOf(10000), quantity);
    }

    private void assertValidationError(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("주문을 생성하면 CREATED 상태이고 총액이 계산된다")
    void create() {
        // when
        Order order = Order.create(1L, List.of(lineItem(101L, 2), lineItem(102L, 1)));

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("회원 ID 가 없으면 생성에 실패한다")
    void failWithoutMemberId() {
        assertValidationError(() -> Order.create(null, List.of(lineItem(101L, 1))));
    }

    @Test
    @DisplayName("상품 줄은 1~20개, 같은 상품은 한 번만 담을 수 있다")
    void lineRules() {
        List<Order.LineItem> twentyOne = LongStream.rangeClosed(1, 21).mapToObj(id -> lineItem(id, 1)).toList();

        assertValidationError(() -> Order.create(1L, List.of()));
        assertValidationError(() -> Order.create(1L, twentyOne));
        assertValidationError(() -> Order.create(1L, List.of(lineItem(101L, 1), lineItem(101L, 2))));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    @DisplayName("상품 한 줄의 수량은 1~10개다")
    void quantityRange(int quantity) {
        assertValidationError(() -> Order.create(1L, List.of(lineItem(101L, quantity))));
    }

    @Test
    @DisplayName("주문 라인은 외부에서 수정할 수 없다")
    void orderLinesAreReadOnly() {
        Order order = Order.create(1L, List.of(lineItem(101L, 1)));

        assertThatThrownBy(() -> order.getOrderLines().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
