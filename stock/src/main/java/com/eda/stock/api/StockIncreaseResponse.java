package com.eda.stock.api;

import com.eda.stock.application.StockIncreaseService;

// availableQuantity 는 Redis 가용 재고. Redis 키가 없거나 반영에 실패하면 null 이다.
public record StockIncreaseResponse(Long productId, int quantity, Long availableQuantity) {

    public static StockIncreaseResponse from(StockIncreaseService.Result result) {
        return new StockIncreaseResponse(result.productId(), result.quantity(), result.availableQuantity());
    }
}
