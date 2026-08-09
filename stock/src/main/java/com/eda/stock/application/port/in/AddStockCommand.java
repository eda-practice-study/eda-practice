package com.eda.stock.application.port.in;

public record AddStockCommand(
        Long productId,
        int quantity
) {
}
