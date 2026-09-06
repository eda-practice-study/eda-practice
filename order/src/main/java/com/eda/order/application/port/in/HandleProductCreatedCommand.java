package com.eda.order.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

public record HandleProductCreatedCommand(
        UUID eventId,
        Long productId,
        String name,
        BigDecimal price
) {
}
