package com.eda.common.event;

import java.util.UUID;

public record StockDeductedEvent(
        UUID eventId,
        Long orderId
) {
}
