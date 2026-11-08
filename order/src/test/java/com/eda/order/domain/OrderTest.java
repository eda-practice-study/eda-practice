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

    @Test
    @DisplayName("주문을 생성하면 CREATED 상태이고 총액이 계산된다")
    void create_shouldCalculateTotal_whenItemsValid() {
        // when
        Order order = Order.create(1L, List.of(lineItem(101L, 2), lineItem(102L, 1)));

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("회원 ID 가 없으면 생성에 실패한다")
    void create_shouldReject_whenMemberIdMissing() {
        assertThatThrownBy(() -> Order.create(null, List.of(lineItem(101L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("상품이 없으면 주문을 생성하지 못한다")
    void create_shouldReject_whenItemsEmpty() {
        assertThatThrownBy(() -> Order.create(1L, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("상품 항목이 20개를 넘으면 주문을 생성하지 못한다")
    void create_shouldReject_whenItemsExceedLimit() {
        List<Order.LineItem> items = LongStream.rangeClosed(1, 21).mapToObj(id -> lineItem(id, 1)).toList();

        assertThatThrownBy(() -> Order.create(1L, items))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("같은 상품을 두 번 담으면 주문을 생성하지 못한다")
    void create_shouldReject_whenProductDuplicated() {
        assertThatThrownBy(() -> Order.create(1L, List.of(lineItem(101L, 1), lineItem(101L, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    @DisplayName("상품 한 줄의 수량은 1~10개다")
    void create_shouldReject_whenQuantityOutsideRange(int quantity) {
        assertThatThrownBy(() -> Order.create(1L, List.of(lineItem(101L, quantity))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("주문 라인은 외부에서 수정할 수 없다")
    void getOrderLines_shouldPreventChanges_whenOrderCreated() {
        Order order = Order.create(1L, List.of(lineItem(101L, 1)));

        assertThatThrownBy(() -> order.getOrderLines().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
