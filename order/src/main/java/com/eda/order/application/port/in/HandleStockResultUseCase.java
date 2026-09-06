package com.eda.order.application.port.in;

import com.eda.common.event.StockDeductionResult;

public interface HandleStockResultUseCase {

    void handle(StockDeductionResult event);
}
