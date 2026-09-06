package com.eda.order.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "product_catalog")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCatalogItem {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    private ProductCatalogItem(
            Long productId,
            String productName,
            BigDecimal unitPrice
    ) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
    }

    public static ProductCatalogItem create(
            Long productId,
            String productName,
            BigDecimal unitPrice
    ) {
        return new ProductCatalogItem(
                productId,
                productName,
                unitPrice
        );
    }
}