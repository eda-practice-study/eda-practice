package com.eda.stock.adapter.in.web.dto;

import com.eda.stock.application.port.in.AddStockUseCase.AddStockCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddStockRequest(

        @NotNull(message = "상품 ID는 필수입니다")
        Long productId,

        @Positive(message = "수량은 0보다 커야 합니다")
        int quantity
) {

    public AddStockCommand toCommand() {
        return new AddStockCommand(productId, quantity);
    }
}
