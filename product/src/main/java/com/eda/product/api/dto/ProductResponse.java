package com.eda.product.api.dto;

import com.eda.product.domain.Product;
import java.time.LocalDateTime;

public record ProductResponse(
        Long productId,
        String name,
        long price,
        LocalDateTime createdAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice().longValueExact(),
                product.getCreatedAt());
    }
}
