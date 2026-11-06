package com.eda.common.message;

import java.math.BigDecimal;

public record ProductCreated(
        Long productId,
        String name,
        BigDecimal price
) {
}
