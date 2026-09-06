package com.eda.common.event;

public record StockDeducted(
        String eventId,
        Long orderId
) implements StockDeductionResult {
}
