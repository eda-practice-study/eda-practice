package com.eda.stock.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
    boolean existsByProductId(Long productId);

    Optional<Stock> findByProductId(Long productId);
}
