package com.eda.common.event;

public sealed interface StockDeductionResult permits StockDeducted, StockDeductionFailed {

    String eventId();

    Long orderId();
}
