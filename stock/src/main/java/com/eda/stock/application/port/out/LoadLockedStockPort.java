package com.eda.stock.application.port.out;

import com.eda.stock.domain.Stock;
import java.util.Collection;
import java.util.List;

public interface LoadLockedStockPort {

    List<Stock> findAllByProductIdsForUpdate(Collection<Long> productIds);
}
