package com.eda.product.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ProductCreateRequest(

        @NotBlank(message = "상품명은 필수입니다.")
        String productName,

        @NotNull(message = "상품 가격은 필수입니다.")
        @Positive(message = "상품 가격은 양수여야 합니다.")
        BigDecimal price
) {
}
