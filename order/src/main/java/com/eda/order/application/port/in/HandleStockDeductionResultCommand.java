package com.eda.order.application.port.in;

import com.eda.common.event.StockDeductionResultEvent;

import java.util.UUID;

public record HandleStockDeductionResultCommand(
        UUID eventId,
        Long orderId,
        StockDeductionResultEvent.Result result
) {
}
