package com.eda.product.adapter.in.web.dto;

import java.math.BigDecimal;

public record CreateProductRequest(
        String name,
        BigDecimal price) {
}
