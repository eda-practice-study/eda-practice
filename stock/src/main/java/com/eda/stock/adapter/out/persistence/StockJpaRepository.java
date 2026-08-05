package com.eda.stock.adapter.out.persistence;

import com.eda.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockJpaRepository extends JpaRepository<Stock, Long> {
}
