package com.eda.product.dto;

import java.time.LocalDateTime;

public record CreateProductResponse(
        Long productId,
        String name,
        long price,
        LocalDateTime createdAt
) {
}
