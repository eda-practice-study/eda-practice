package com.eda.common.event;

import java.util.UUID;

public record StockDeductionFailedEvent(
            UUID eventId,
            Long orderId,
            Reason reason
) {
    public enum Reason {
        INSUFFICIENT_STOCK
    }
}
