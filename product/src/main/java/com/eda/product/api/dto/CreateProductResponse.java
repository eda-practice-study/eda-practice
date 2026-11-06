package com.eda.product.api.dto;

import com.eda.product.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateProductResponse(
        Long productId,
        String name,
        BigDecimal price,
        LocalDateTime createdAt
) {

    public static CreateProductResponse from(Product product) {
        return new CreateProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCreatedAt()
        );
    }
}
