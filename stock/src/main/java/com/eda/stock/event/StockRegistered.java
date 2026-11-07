package com.eda.stock.event;

public record StockRegistered(Long productId, int quantity, long version) {
}
