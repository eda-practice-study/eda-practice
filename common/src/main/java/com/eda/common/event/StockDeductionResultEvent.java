package com.eda.common.event;

import java.time.Instant;
import java.util.UUID;

public record StockDeductionResultEvent(
        UUID eventId,
        Long orderId,
        Result result,
        Instant occurredAt
) {
    public enum Result {
        DEDUCTED,
        INSUFFICIENT_STOCK
    }
}
