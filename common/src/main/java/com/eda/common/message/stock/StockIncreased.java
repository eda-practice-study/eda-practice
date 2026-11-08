package com.eda.common.message.stock;

public record StockIncreased(
        Long productId,
        int quantity
) {
}
