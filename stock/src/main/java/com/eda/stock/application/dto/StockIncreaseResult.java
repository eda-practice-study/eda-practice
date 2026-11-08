package com.eda.stock.application.dto;

public record StockIncreaseResult(
        Long productId,
        int quantity
) {
}
