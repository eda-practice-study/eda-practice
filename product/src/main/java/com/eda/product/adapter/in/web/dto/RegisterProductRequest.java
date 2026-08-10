package com.eda.product.adapter.in.web.dto;

import com.eda.product.application.port.in.RegisterProductUseCase.RegisterProductCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RegisterProductRequest(

        @NotBlank(message = "상품명은 필수입니다")
        String name,

        @NotNull(message = "가격은 필수입니다")
        @Positive(message = "가격은 0보다 커야 합니다")
        BigDecimal price
) {

    public RegisterProductCommand toCommand() {
        return new RegisterProductCommand(name, price);
    }
}
