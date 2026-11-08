package com.eda.stock.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record IncreaseStockRequest(
        @NotNull
        @Min(1)
        @Max(10_000)
        int quantity
) {
}
