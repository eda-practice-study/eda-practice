package com.eda.product.application.port.in;

import java.math.BigDecimal;

public record  CreateProductCommand (
        String name,
        BigDecimal price
) {}
