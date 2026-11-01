package com.eda.stock.infrastructure;

import com.eda.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {

    boolean existsByProductId(Long productId);
}
