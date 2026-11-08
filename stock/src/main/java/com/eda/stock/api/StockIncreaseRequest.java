package com.eda.stock.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockIncreaseRequest(@NotNull @Min(1) @Max(10000) Integer quantity) {
}
