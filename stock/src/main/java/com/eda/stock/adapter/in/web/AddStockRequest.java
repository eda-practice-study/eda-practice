package com.eda.stock.adapter.in.web;

import com.eda.stock.application.port.in.AddStockCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddStockRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        @Positive(message = "상품 ID는 0보다 커야합니다.")
        Long productId,

        @NotNull(message = "추가 수량은 필수입니다.")
        @Positive(message = "추가 수량은 0보다 커야합니다.")
        Integer quantity
) {
    public AddStockCommand toCommand() {
        return new AddStockCommand(
                productId,
                quantity
        );
    }
}
