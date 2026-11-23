package com.eda.stock.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDecreaseRepository extends JpaRepository<StockDecrease, Long> {

    boolean existsByOrderId(Long orderId);
}
