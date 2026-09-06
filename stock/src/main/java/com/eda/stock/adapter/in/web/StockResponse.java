package com.eda.stock.adapter.in.web;

import com.eda.stock.domain.Stock;

public record StockResponse(
        Long productId,
        int quantity
) {
    public static StockResponse from (Stock stock) {
        return new StockResponse(
                stock.getProductId(),
                stock.getQuantity()
        );
    }
}
