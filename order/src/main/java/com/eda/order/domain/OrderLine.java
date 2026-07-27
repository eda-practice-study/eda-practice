package com.eda.order.domain;

import com.eda.common.domain.BaseEntity;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine extends BaseEntity {

    @Column(name = "product_id", nullable = false, comment = "상품 ID (값 참조)")
    private Long productId;

    @Column(nullable = false, comment = "주문 시점 상품명 스냅샷")
    private String productName;

    @Column(nullable = false, precision = 19, scale = 2, comment = "주문 시점 금액 스냅샷")
    private BigDecimal unitPrice;

    @Column(nullable = false, comment = "주문 수량")
    private int quantity;

    @Column(nullable = false, comment = "환불된 수량")
    private int refundedQuantity;

    private OrderLine(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.refundedQuantity = 0;
    }

    static OrderLine of(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        if (productId == null || productName == null || unitPrice == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new OrderLine(productId, productName, unitPrice, quantity);
    }

    void refund(int refundQuantity) {
        if (refundQuantity <= 0 || refundQuantity > getRefundableQuantity()) {
            throw new BusinessException(ErrorCode.INVALID_REFUND);
        }

        this.refundedQuantity += refundQuantity;
    }

    // 라인 소계 = 단가 × 수량
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    // 환불 가능한 수량
    public int getRefundableQuantity() {
        return quantity - refundedQuantity;
    }
}
