package com.eda.stock.application.port.out;

import com.eda.stock.domain.Stock;

import java.util.Optional;

public interface FindStockPort {
    Optional<Stock> findByProductId(Long productId);
}
