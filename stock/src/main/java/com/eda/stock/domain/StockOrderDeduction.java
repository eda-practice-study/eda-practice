package com.eda.stock.domain;

import com.eda.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_order_deduction", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockOrderDeduction extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true, comment = "주문 ID")
    private Long orderId;

    @Column(nullable = false, length = 100, comment = "차감 결과 이벤트 ID")
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, comment = "차감 결과")
    private StockDeductionStatus status;

    @Column(length = 50, comment = "차감 실패 사유")
    private String failureReason;

    private StockOrderDeduction(Long orderId, String eventId, StockDeductionStatus status, String failureReason) {
        this.orderId = orderId;
        this.eventId = eventId;
        this.status = status;
        this.failureReason = failureReason;
    }

    public static StockOrderDeduction success(Long orderId, String eventId) {
        return new StockOrderDeduction(orderId, eventId, StockDeductionStatus.SUCCEEDED, null);
    }

    public static StockOrderDeduction failure(Long orderId, String eventId, String reason) {
        return new StockOrderDeduction(orderId, eventId, StockDeductionStatus.FAILED, reason);
    }
}
