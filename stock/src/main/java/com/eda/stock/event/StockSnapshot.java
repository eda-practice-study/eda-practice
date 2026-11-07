package com.eda.stock.event;

public record StockSnapshot(Long productId, int quantity, long version) {
    public static final String TOPIC = "stock.events";
}
