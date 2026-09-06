package com.eda.stock.application.port.out;

import com.eda.stock.domain.Stock;

import java.util.List;
import java.util.Optional;

public interface LoadStockForUpdatePort {
    Optional<Stock> loadByProductIdForUpdate(Long productId);

    List<Stock> loadAllByProductIdsForUpdate(List<Long> productIds);
}
