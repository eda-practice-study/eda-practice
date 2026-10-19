package com.eda.order.domain;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "order_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderLine {

    public static final int MAX_QUANTITY = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, comment = "상품 ID")
    private Long productId;

    @Column(nullable = false, comment = "주문 시점 상품명 스냅샷")
    private String productName;

    @Column(nullable = false, precision = 19, scale = 0, comment = "주문 시점 단가 스냅샷 (원)")
    private BigDecimal unitPrice;

    @Column(nullable = false, comment = "주문 수량")
    private int quantity;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, comment = "생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, comment = "수정 시각")
    private LocalDateTime updatedAt;

    private OrderLine(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    static OrderLine of(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        if (productId == null || productName == null || productName.isBlank() || unitPrice == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new OrderLine(productId, productName, unitPrice, quantity);
    }

    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
