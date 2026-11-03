package com.eda.stock.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record IncreaseStockRequest(
        @NotNull(message = "증량 수량은 필수입니다.")
        @Min(value = 1, message = "증량 수량은 1 이상어야 합니다.")
        @Max(value = 10_000, message = "증량 수량은 10000 이하어야 합니다.")
        Integer quantity
) {
}
