package com.eda.stock.api;

public record StockIncreaseResponse(Long productId, int quantity, Long availableQuantity) {
}
