package com.eda.stock.application.port.in;

import java.util.List;
import java.util.UUID;

public record HandleOrderCreatedCommand(
        UUID eventId,
        Long orderId,
        List<Line> lines
) {
    public record Line (
            Long productId,
            int quantity
    ) {}
}
