package com.eda.payment.domain;

import com.eda.common.domain.BaseEntity;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(nullable = false, unique = true, comment = "결제 건 식별키 (멱등 키)")
    private String paymentKey;

    @Column(nullable = false, comment = "주문 ID (값 참조)")
    private Long orderId;

    @Column(nullable = false, precision = 19, scale = 2, comment = "결제 금액")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, comment = "결제 상태")
    private PaymentStatus status;

    @Column(nullable = false, precision = 19, scale = 2, comment = "환불 누계")
    private BigDecimal refundedAmount;

    private Payment(String paymentKey, Long orderId, BigDecimal amount) {
        this.paymentKey = paymentKey;
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        this.refundedAmount = BigDecimal.ZERO;
    }

    public static Payment create(String paymentKey, Long orderId, BigDecimal amount) {
        validate(paymentKey, orderId, amount);
        return new Payment(paymentKey, orderId, amount);
    }

    public void complete() {
        requirePending();
        this.status = PaymentStatus.PAID;
    }

    public void fail() {
        requirePending();
        this.status = PaymentStatus.FAILED;
    }

    public void refund(BigDecimal refundAmount) {
        if (status != PaymentStatus.PAID && status != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new BusinessException(ErrorCode.INVALID_REFUND);
        }

        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REFUND);
        }

        BigDecimal newRefunded = this.refundedAmount.add(refundAmount);
        if (newRefunded.compareTo(this.amount) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REFUND);
        }

        this.refundedAmount = newRefunded;

        // 전액이면 REFUNDED, 일부면 PARTIALLY_REFUNDED
        this.status = newRefunded.compareTo(this.amount) == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
    }

    private void requirePending() {
        if (this.status != PaymentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    private static void validate(String paymentKey, Long orderId, BigDecimal amount) {
        if (paymentKey == null || paymentKey.isBlank() || orderId == null || amount == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
