package com.eda.product.api;

import com.eda.product.domain.Product;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductCreateRequest(
        @NotBlank @Size(max = Product.MAX_NAME_LENGTH) String name,
        @NotNull @DecimalMin("1") @DecimalMax("1000000") BigDecimal price
) {
}
