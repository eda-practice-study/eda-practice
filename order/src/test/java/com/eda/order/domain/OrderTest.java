package com.eda.order.domain;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderTest {

    // --- fixtures ---
    private Order.LineItem lineItem(long productId, int quantity) {
        return new Order.LineItem(productId, "상품" + productId, BigDecimal.valueOf(10000), quantity);
    }

    private Order createdOrder() {
        return Order.create(1L, List.of(lineItem(101L, 2), lineItem(102L, 1)));
    }

    private Order completedOrder() {
        Order order = createdOrder();
        order.markStockReserved();
        order.markPaid();
        order.markCompleted();
        return order;
    }

    @Test
    @DisplayName("주문을 생성하면 CREATED 상태이고 총액이 계산된다")
    void createOrder() {
        // when
        Order order = Order.create(1L, List.of(lineItem(101L, 2), lineItem(102L, 1)));

        // then
        assertThat(order.getMemberId()).isEqualTo(1L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getOrderLines()).hasSize(2);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("30000"); // 10000*2 + 10000*1
    }

    @Test
    @DisplayName("주문 항목이 비어 있으면 생성에 실패한다")
    void failToCreateWithoutItems() {
        // when & then
        assertThatThrownBy(() -> Order.create(1L, List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("같은 상품이 중복되면 생성에 실패한다")
    void failToCreateWithDuplicateProduct() {
        // when & then
        assertThatThrownBy(() -> Order.create(1L, List.of(lineItem(101L, 1), lineItem(101L, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("회원 ID 없이 주문을 생성하면 실패한다")
    void failToCreateWithoutMemberId() {
        // when & then
        assertThatThrownBy(() -> Order.create(null, List.of(lineItem(101L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("주문 상태가 사가 순서대로 전이된다")
    void transitionThroughSagaStates() {
        // given
        Order order = createdOrder();

        // when & then
        order.markStockReserved();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);

        order.markPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);

        order.markCompleted();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("허용되지 않는 상태 전이는 실패한다")
    void failOnInvalidTransition() {
        // given: CREATED 상태에서 바로 결제 완료 불가
        Order order = createdOrder();

        // when & then
        assertThatThrownBy(order::markPaid)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("CREATED 가 아니면 재고 예약 전이에 실패한다")
    void failToMarkStockReservedWhenNotCreated() {
        // given: 이미 STOCK_RESERVED
        Order order = createdOrder();
        order.markStockReserved();

        // when & then
        assertThatThrownBy(order::markStockReserved)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("PAID 가 아니면 완료 전이에 실패한다")
    void failToMarkCompletedWhenNotPaid() {
        // given: CREATED 상태
        Order order = createdOrder();

        // when & then
        assertThatThrownBy(order::markCompleted)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("CREATED 상태에서 주문을 취소할 수 있다")
    void cancelOrder() {
        // given
        Order order = createdOrder();

        // when
        order.cancel(CancelReason.INSUFFICIENT_STOCK);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("완료된 주문은 취소할 수 없다")
    void failToCancelCompletedOrder() {
        // given
        Order order = completedOrder();

        // when & then
        assertThatThrownBy(() -> order.cancel(CancelReason.INSUFFICIENT_STOCK))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("일부 수량만 환불하면 PARTIALLY_REFUNDED 상태가 된다")
    void partialRefund() {
        // given
        Order order = completedOrder(); // 101:2, 102:1

        // when
        order.refund(Map.of(101L, 1));

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PARTIALLY_REFUNDED);
    }

    @Test
    @DisplayName("모든 수량을 환불하면 REFUNDED 상태가 된다")
    void fullRefund() {
        // given
        Order order = completedOrder(); // 101:2, 102:1

        // when
        order.refund(Map.of(101L, 2, 102L, 1));

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("환불 가능 수량을 초과하면 실패하고 아무것도 적용되지 않는다")
    void failAndApplyNothingWhenRefundExceeds() {
        // given
        Order order = completedOrder(); // 101:2, 102:1

        // when & then: 101은 정상(2)이지만 102가 초과(5) → 전체 실패
        assertThatThrownBy(() -> order.refund(Map.of(101L, 2, 102L, 5)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFUND);

        // 부분 적용 없음: 상태 그대로이고, 정상이던 101 라인도 환불되지 않음
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        int line101Refunded = order.getOrderLines().stream()
                .filter(line -> line.getProductId().equals(101L))
                .findFirst().orElseThrow()
                .getRefundedQuantity();
        assertThat(line101Refunded).isZero();
    }

    @Test
    @DisplayName("주문에 없는 상품을 환불하면 실패한다")
    void failToRefundUnknownProduct() {
        // given
        Order order = completedOrder();

        // when & then
        assertThatThrownBy(() -> order.refund(Map.of(999L, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFUND);
    }

    @Test
    @DisplayName("완료되지 않은 주문은 환불할 수 없다")
    void failToRefundNotCompletedOrder() {
        // given: CREATED 상태
        Order order = createdOrder();

        // when & then
        assertThatThrownBy(() -> order.refund(Map.of(101L, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("getOrderLines 는 불변 뷰라 외부에서 변경할 수 없다")
    void orderLinesAreUnmodifiable() {
        // given
        Order order = createdOrder();

        // when & then
        assertThatThrownBy(() -> order.getOrderLines().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
