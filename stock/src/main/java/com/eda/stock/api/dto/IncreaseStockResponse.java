package com.eda.stock.api.dto;

public record IncreaseStockResponse(
        Long productId,
        int quantity,
        Integer availableQuantity
) {
    public static IncreaseStockResponse from(Long productId, int quantity, Integer availableQuantity) {

        return new IncreaseStockResponse(productId, quantity, availableQuantity);
    }
}
