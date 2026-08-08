package com.eda.stock.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockAddRequest(

        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @NotNull(message = "상품 재고 수량은 필수입니다.")
        @Positive(message = "상품 재고 수량은 양수여야 합니다.")
        Integer quantity
) {
}
