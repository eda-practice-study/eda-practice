package com.eda.stock.adapter.in.web.dto;

import com.eda.stock.application.port.in.AddStockCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddStockRequest(
        @NotNull
        @Positive
        Long productId,

        @NotNull
        @Positive
        Integer quantity
) {
    public AddStockCommand toCommand() {
        return new AddStockCommand(productId, quantity);
    }
}
