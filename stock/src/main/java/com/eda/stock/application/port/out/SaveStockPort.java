package com.eda.stock.application.port.out;

import com.eda.stock.domain.Stock;

import java.util.Collection;
import java.util.List;

public interface SaveStockPort {
    Stock save(Stock stock);
    List<Stock> saveAll(Collection<Stock> stocks);
}
