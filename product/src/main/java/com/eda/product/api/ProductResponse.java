package com.eda.product.api;

import com.eda.product.domain.Product;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public record ProductResponse(
        Long productId,
        String name,
        long price,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice().longValueExact(),
                product.getCreatedAt());
    }
}
