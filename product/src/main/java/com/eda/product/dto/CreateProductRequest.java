package com.eda.product.dto;

import com.eda.product.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank @Size(max = Product.MAX_NAME_LENGTH) String name,
        @NotNull BigDecimal price
) {
}
