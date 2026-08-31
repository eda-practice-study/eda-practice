package com.eda.stock.domain;

import com.eda.common.domain.BaseEntity;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock", uniqueConstraints = @UniqueConstraint(columnNames = "product_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true, comment = "상품 ID")
    private Long productId;

    @Column(nullable = false, comment = "재고 수량")
    private int quantity;

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

    public void add(int quantity) {
        requirePositive(quantity);
        this.quantity += quantity;
    }

    public boolean canDeduct(int quantity) {
        requirePositive(quantity);
        return this.quantity >= quantity;
    }

    public void deduct(int quantity) {
        requirePositive(quantity);
        if (this.quantity < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }
        this.quantity -= quantity;
    }

    public void restore(int quantity) {
        requirePositive(quantity);
        this.quantity += quantity;
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_OPERATION);
        }
    }
}
