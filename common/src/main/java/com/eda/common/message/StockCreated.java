package com.eda.common.message;

public record StockCreated(
        Long stockId,
        Long productId,
        int quantity
) {
}
