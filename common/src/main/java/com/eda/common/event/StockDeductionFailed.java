package com.eda.common.event;

public record StockDeductionFailed(
        String eventId,
        Long orderId,
        String reason
) implements StockDeductionResult {
}
