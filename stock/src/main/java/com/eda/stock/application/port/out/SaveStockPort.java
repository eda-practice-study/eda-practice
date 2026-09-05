package com.eda.stock.application.port.out;

import com.eda.stock.domain.Stock;

public interface SaveStockPort {

    void save(Stock stock);

}
