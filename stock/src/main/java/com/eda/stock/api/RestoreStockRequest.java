package com.eda.stock.api;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RestoreStockRequest(
        @NotNull(message = "주문 키는 필수입니다")
        UUID orderKey
) {
}
