package com.eda.stock.adapter.in.web.dto;

public record AddStockResponse(
        Long productId,
        int quantity
) {
}
