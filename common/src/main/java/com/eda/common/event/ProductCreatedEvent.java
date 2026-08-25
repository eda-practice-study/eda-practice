package com.eda.common.event;

import java.math.BigDecimal;

public record ProductCreatedEvent(
        Long productId,
        String name,
        BigDecimal price
) {
}
