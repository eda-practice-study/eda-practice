package com.eda.stock.application.port.out;

import com.eda.stock.domain.Stock;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FindStockPort {
    Optional<Stock> findByProductId(Long productId);
    List<Stock> findAllByProductIdIn(Collection<Long> productIds);
}
