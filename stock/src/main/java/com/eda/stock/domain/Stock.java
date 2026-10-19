package com.eda.stock.domain;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, comment = "상품 ID")
    private Long productId;

    @Column(nullable = false, comment = "실재고 수량")
    private int quantity;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, comment = "생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, comment = "수정 시각")
    private LocalDateTime updatedAt;

    private Stock(Long productId) {
        this.productId = productId;
        this.quantity = 0;
    }

    public static Stock createFor(Long productId) {
        if (productId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new Stock(productId);
    }

    public void increase(int quantity) {
        requirePositive(quantity);
        this.quantity += quantity;
    }

    public void decrease(int quantity) {
        requirePositive(quantity);
        if (this.quantity < quantity) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        this.quantity -= quantity;
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
    }
}
