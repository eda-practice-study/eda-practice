package com.eda.stock.application.port.out;

import com.eda.common.event.StockDeductionResultEvent;

public interface SaveStockResultOutboxPort {

    void save(StockDeductionResultEvent event);
}
