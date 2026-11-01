package com.eda.stock.api.dto;

import com.eda.stock.domain.Stock;

// quantity = DB 실재고, availableQuantity = Redis 가용 재고 (Redis 반영 전이거나 실패하면 null)
public record StockResponse(Long productId, int quantity, Long availableQuantity) {

    public static StockResponse of(Stock stock, Long availableQuantity) {
        return new StockResponse(stock.getProductId(), stock.getQuantity(), availableQuantity);
    }
}
