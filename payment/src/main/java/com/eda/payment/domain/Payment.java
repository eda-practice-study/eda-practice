package com.eda.payment.domain;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, comment = "주문 ID")
    private Long orderId;

    @Column(nullable = false, comment = "결제 회원 ID")
    private Long memberId;

    @Column(nullable = false, precision = 19, scale = 0, comment = "결제 금액")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, comment = "결제 상태")
    private PaymentStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, comment = "생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, comment = "수정 시각")
    private LocalDateTime updatedAt;

    private Payment(Long orderId, Long memberId, BigDecimal amount) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long orderId, Long memberId, BigDecimal amount) {
        if (orderId == null || memberId == null || amount == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new Payment(orderId, memberId, amount);
    }
}
