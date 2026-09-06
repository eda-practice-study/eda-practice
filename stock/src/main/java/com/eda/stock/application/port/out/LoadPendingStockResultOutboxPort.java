package com.eda.stock.application.port.out;

import com.eda.common.event.StockDeductionResultEvent;

import java.util.List;

public interface LoadPendingStockResultOutboxPort {

    List<StockDeductionResultEvent> loadPending();
}
