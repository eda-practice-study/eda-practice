package com.eda.product.adapter.in.web.dto;

import com.eda.product.application.port.in.CreateProductCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank
        String name,

        @NotNull
        @Positive
        BigDecimal price
) {
    public CreateProductCommand toCommand() {
        return new CreateProductCommand(name, price);
    }
}
