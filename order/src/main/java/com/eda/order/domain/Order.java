package com.eda.order.domain;

import com.eda.common.domain.BaseEntity;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Column(nullable = false, comment = "주문 회원 ID")
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, comment = "주문 상태")
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 2, comment = "주문 총액")
    private BigDecimal totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<OrderLine> orderLines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private CancelReason cancelReason;

    private Order(Long memberId, List<OrderLine> orderLines) {
        this.memberId = memberId;
        this.orderLines = orderLines;
        this.status = OrderStatus.CREATED;
        this.totalAmount = orderLines.stream()
                .map(OrderLine::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Order create(Long memberId, List<LineItem> items) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (items == null || items.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        // 한 주문에 같은 상품 중복 라인 금지
        if (items.stream().map(LineItem::productId).distinct().count() != items.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        List<OrderLine> orderLines = items.stream()
                .map(item -> OrderLine.of(item.productId(), item.productName(), item.unitPrice(), item.quantity()))
                .collect(Collectors.toCollection(ArrayList::new));

        return new Order(memberId, orderLines);
    }

    public void markStockReserved() {
        transition(OrderStatus.CREATED, OrderStatus.STOCK_RESERVED);
    }

    public void markPaid() {
        transition(OrderStatus.STOCK_RESERVED, OrderStatus.PAID);
    }

    public void markCompleted() {
        transition(OrderStatus.PAID, OrderStatus.COMPLETED);
    }

    // 재고 부족 or 결제 실패로 인한 취소
    public void cancel(CancelReason reason) {
        if (status != OrderStatus.CREATED && status != OrderStatus.STOCK_RESERVED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (reason == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }


        this.status = OrderStatus.CANCELED;
        this.cancelReason = reason;
    }

    public void refund(Map<Long, Integer> refundQuantityByProductId) {
        if (status != OrderStatus.COMPLETED && status != OrderStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (refundQuantityByProductId == null || refundQuantityByProductId.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REFUND);
        }

        // 모든 항목이 적용 가능한지 먼저 전부 확인한 뒤 통과했을 때만 실제로 적용
        refundQuantityByProductId.forEach(this::validateRefundable);
        refundQuantityByProductId.forEach((productId, qty) -> lineOf(productId).refund(qty));

        // 전량 환불이면 REFUNDED, 일부면 PARTIALLY_REFUNDED.
        this.status = orderLines.stream().allMatch(line -> line.getRefundableQuantity() == 0) ?
                OrderStatus.REFUNDED :
                OrderStatus.PARTIALLY_REFUNDED;
    }

    public List<OrderLine> getOrderLines() {
        return List.copyOf(orderLines);
    }

    private void validateRefundable(Long productId, Integer qty) {
        OrderLine line = lineOf(productId);
        if (qty <= 0 || qty > line.getRefundableQuantity()) {
            throw new BusinessException(ErrorCode.INVALID_REFUND);
        }
    }

    private OrderLine lineOf(Long productId) {
        return orderLines.stream()
                .filter(orderLine -> orderLine.getProductId().equals(productId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFUND));
    }

    private void transition(OrderStatus required, OrderStatus target) {
        if (this.status != required) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        this.status = target;
    }

    // Parameter Object
    public record LineItem(Long productId, String productName, BigDecimal unitPrice, int quantity) {
    }
}
