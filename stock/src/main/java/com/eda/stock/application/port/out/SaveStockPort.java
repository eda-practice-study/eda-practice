package com.eda.stock.application.port.out;

import com.eda.stock.domain.Stock;

public interface SaveStockPort {
    Stock save(Stock stock);
}
