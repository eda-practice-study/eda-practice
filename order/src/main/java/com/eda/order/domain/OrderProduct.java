package com.eda.order.domain;

import com.eda.common.domain.BaseEntity;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderProduct extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    private OrderProduct(Long productId, String name, BigDecimal price) {
        this.productId = productId;
        this.name = name;
        this.price = price;
    }

    public static OrderProduct create(Long productId, String name, BigDecimal price) {
        if (productId == null || name == null || name.isBlank() || price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return new OrderProduct(productId, name, price);
    }
}
