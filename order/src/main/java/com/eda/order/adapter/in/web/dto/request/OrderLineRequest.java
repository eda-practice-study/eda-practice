package com.eda.order.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderLineRequest(

        @NotNull(message = "상품 ID는 필수입니다.")
        @Positive(message = "상품 ID는 양수여야 합니다.")
        Long productId,

        @NotNull(message = "주문 수량은 필수입니다.")
        @Positive(message = "주문 수량은 양수여야 합니다.")
        Integer quantity
) {
}
