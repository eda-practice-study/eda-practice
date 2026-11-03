package com.eda.stock.api.dto;

public record IncreaseStockResponse(
        Long productId,
        int quantity,
        Long availableQuantity
) {
}
