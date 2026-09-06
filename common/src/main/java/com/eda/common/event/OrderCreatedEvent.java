package com.eda.common.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID eventId,
        Long orderId,
        List<Line> lines,
        Instant occurredAt
) {
    public record Line(
            Long productId,
            int quantity
    ) {}
}
