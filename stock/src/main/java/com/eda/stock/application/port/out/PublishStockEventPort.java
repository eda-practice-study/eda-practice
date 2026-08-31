package com.eda.stock.application.port.out;

import com.eda.common.event.StockDeductedEvent;
import com.eda.common.event.StockDeductionFailedEvent;

public interface PublishStockEventPort {
    void publish(StockDeductedEvent event);
    void publish(StockDeductionFailedEvent event);
}
