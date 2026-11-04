package com.eda.common.message;

public record ProductCreated(
        Long productId,
        String name,
        long price
) {
}
