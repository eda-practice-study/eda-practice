package com.eda.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductCreatedEvent(
        String eventId,
        Instant occurredAt,
        Long productId,
        String name,
        BigDecimal price
) {

    public static ProductCreatedEvent of(Long productId, String name, BigDecimal price) {
        return new ProductCreatedEvent(UUID.randomUUID().toString(), Instant.now(), productId, name, price);
    }
}
