package com.eda.stock.adapter.in.web.dto.response;

import com.eda.stock.application.port.in.StockAddResult;

public record StockAddResponse(
        Long productId,
        int addedQuantity,
        int totalQuantity
) {
    public static StockAddResponse from(StockAddResult result) {
        return new StockAddResponse(result.productId(), result.addedQuantity(), result.totalQuantity());
    }
}
