package com.eda.common.message;

public record StockIncreased(
        Long productId,
        int increasedQuantity,
        int quantity
) {
}
