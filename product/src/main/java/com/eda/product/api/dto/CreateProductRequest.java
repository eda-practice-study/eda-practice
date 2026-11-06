package com.eda.product.api.dto;

import java.math.BigDecimal;

import com.eda.product.domain.Product;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(
        @NotBlank(message = "상품명은 필수 입력 정보입니다.")
        @Size(max = Product.MAX_NAME_LENGTH, message = "상품명은 최대 100자까지 입력할 수 있습니다.")
        String name,

        @NotNull(message = "가격은 필수 입력 정보입니다.")
        @DecimalMin(value = "1", message = "가격은 1원 이상이어야 합니다.")
        @DecimalMax(value = "1000000", message = "가격은 1,000,000원 이하여야 합니다.")
        @Digits(integer = 7, fraction = 0, message = "가격은 원 단위 정수여야 합니다.")
        BigDecimal price
) {
}
