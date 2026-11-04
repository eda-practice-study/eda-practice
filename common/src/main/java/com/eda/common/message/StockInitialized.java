package com.eda.common.message;

public record StockInitialized(
        Long productId,
        int quantity
) {
}
