package com.eda.common.event;

import java.time.Instant;
import java.util.UUID;

public record ProductCreatedEvent(
        UUID eventId,
        Long productId,
        Instant occurredAt
) {
}
