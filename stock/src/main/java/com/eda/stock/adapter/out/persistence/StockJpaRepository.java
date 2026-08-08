package com.eda.stock.adapter.out.persistence;

import com.eda.stock.domain.Stock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockJpaRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByProductId(Long productId);

    boolean existsByProductId(Long productId);
}
