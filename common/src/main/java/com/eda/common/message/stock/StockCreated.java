package com.eda.common.message.stock;

public record StockCreated(
        Long stockId,
        Long productId,
        int quantity
) {
}
