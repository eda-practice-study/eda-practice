package com.eda.stock.event;

public record StockIncreased(Long productId, int quantity, long version) {
}
