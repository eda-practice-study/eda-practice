package com.eda.product.domain;

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
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(nullable = false, comment = "상품명")
    private String name;

    @Column(nullable = false, precision = 19, scale = 2, comment = "상품 가격")
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, comment = "판매 상태")
    private ProductStatus status;

    private Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
        this.status = ProductStatus.ACTIVE;
    }

    public static Product register(String name, BigDecimal price) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new Product(name, price);
    }

    public void deactivate() {
        this.status = ProductStatus.DEACTIVATED;
    }
}
