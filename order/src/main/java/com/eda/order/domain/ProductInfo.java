package com.eda.order.domain;

import com.eda.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(
        name = "product_info",
        uniqueConstraints = @UniqueConstraint(columnNames = "product_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInfo extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    private ProductInfo(
            Long productId,
            String name,
            BigDecimal price
    ) {
        this.productId = productId;
        this.name = name;
        this.price = price;
    }

    public static ProductInfo create(
            Long productId,
            String name,
            BigDecimal price
    ) {
        return new ProductInfo(productId, name, price);
    }

}
