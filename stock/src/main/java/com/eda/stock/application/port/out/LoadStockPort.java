package com.eda.stock.application.port.out;

import com.eda.stock.domain.Stock;

public interface LoadStockPort {

    Stock load(Long productId);

    boolean existsByProductId(Long productId);
}
