package com.eda.stock.application.port.out;

import com.eda.stock.domain.Stock;
import java.util.Optional;

public interface LoadStockPort {

    boolean existsByProductId(Long productId);

    Optional<Stock> findByProductId(Long productId);
}
