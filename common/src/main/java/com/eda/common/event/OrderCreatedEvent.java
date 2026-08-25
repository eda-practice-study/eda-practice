package com.eda.common.event;

import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        Long orderId,
        List<Line> lines
) {
    public record Line(
            Long productId,
            int quantity
    ) {}
}
