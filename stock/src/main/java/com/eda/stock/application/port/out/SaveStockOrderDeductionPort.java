package com.eda.stock.application.port.out;

import com.eda.stock.domain.StockOrderDeduction;

public interface SaveStockOrderDeductionPort {

    StockOrderDeduction save(StockOrderDeduction deduction);
}
