package com.eda.product.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 메시지를 적어 두지 않으면 요청 언어(Accept-Language)나 JVM locale 에 따라 바뀐다
public record CreateProductRequest(
        @NotBlank(message = "상품명은 필수입니다")
        @Size(max = 100, message = "100자 이하여야 합니다")
        String name,

        @NotNull(message = "가격은 필수입니다")
        @Min(value = 1, message = "1 이상이어야 합니다")
        @Max(value = 1_000_000, message = "1000000 이하여야 합니다")
        Integer price
) {
}
