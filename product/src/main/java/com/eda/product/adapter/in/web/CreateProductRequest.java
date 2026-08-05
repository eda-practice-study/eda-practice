package com.eda.product.adapter.in.web;

import com.eda.product.application.port.in.CreateProductCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank(message = "상품명은 필수입니다.")
        String name,

        @NotNull(message = "상품 가격은 필수입니다.")
        @Positive(message = "상품 가격은 0보다 커야합니다.")
        BigDecimal price
) {
    public CreateProductCommand toCommand() {
        return new CreateProductCommand(
                name,
                price
        );
    }
}
