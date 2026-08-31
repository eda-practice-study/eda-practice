package com.eda.order.application.port.in;

import java.math.BigDecimal;

public record CreateOrderProductCommand(
        Long productId,
        String name,
        BigDecimal price
) {
}
