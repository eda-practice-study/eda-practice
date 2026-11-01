package com.eda.product.api;

import com.eda.product.domain.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductCreateResponse(Long productId, String name, BigDecimal price, LocalDateTime createdAt) {

    public static ProductCreateResponse from(Product product) {
        return new ProductCreateResponse(product.getId(), product.getName(), product.getPrice(), product.getCreatedAt());
    }
}
