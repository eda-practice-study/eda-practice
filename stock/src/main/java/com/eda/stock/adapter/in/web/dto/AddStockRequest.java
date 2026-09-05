package com.eda.stock.adapter.in.web.dto;

public record AddStockRequest(
        int quantity,
        Long productId
) {
}
