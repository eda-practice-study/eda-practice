package com.eda.product.api.dto;

import com.eda.product.domain.Product;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductRequest (
        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = Product.MAX_NAME_LENGTH, message = "상품명은 100 이하여야 합니다.")
        String name,

        @NotNull(message = "가격은 필수입니다.")
        @DecimalMin(value = "1", message = "가격은 1 이상이어야 합니다.")
        @DecimalMax(value = Product.MAX_PRICE_VALUE, message = "가격은 " + Product.MAX_PRICE_VALUE + "이하여야 합니다.")
        BigDecimal price
) {
}
