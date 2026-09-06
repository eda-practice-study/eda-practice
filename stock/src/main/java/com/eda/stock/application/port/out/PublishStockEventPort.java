package com.eda.stock.application.port.out;

import com.eda.common.event.StockDeductionResult;

public interface PublishStockEventPort {

    void publish(StockDeductionResult event);
}
