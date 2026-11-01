package com.eda.stock.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// 메시지를 적어 두지 않으면 요청 언어(Accept-Language)나 JVM locale 에 따라 바뀐다
public record IncreaseStockRequest(
        @NotNull(message = "수량은 필수입니다")
        @Min(value = 1, message = "1 이상이어야 합니다")
        @Max(value = 10_000, message = "10000 이하여야 합니다")
        Integer quantity
) {
}
