package com.eda.stock.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Boolean existsByProductId(Long productId);
}
