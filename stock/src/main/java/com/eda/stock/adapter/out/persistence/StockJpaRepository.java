package com.eda.stock.adapter.out.persistence;

import com.eda.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockJpaRepository extends JpaRepository<Stock, Long> {

    boolean existsByProductId(Long productId);
}
