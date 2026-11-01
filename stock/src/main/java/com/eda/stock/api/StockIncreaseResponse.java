package com.eda.stock.api;

import com.eda.stock.application.StockIncreaseService;

public record StockIncreaseResponse(Long productId, int quantity, Long availableQuantity) {

    public static StockIncreaseResponse from(StockIncreaseService.Result result) {
        return new StockIncreaseResponse(result.productId(), result.quantity(), result.availableQuantity());
    }
}
