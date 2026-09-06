package com.eda.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductCreatedEvent(
        UUID eventId,
        Long productId,
        String name,
        BigDecimal price,
        Instant occurredAt
) {
}
