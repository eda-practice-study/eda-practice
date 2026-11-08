package com.eda.stock.domain;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, Long> {
    boolean existsByProductId(Long productId);

    Optional<Stock> findByProductId(Long productId);

    List<Stock> findByProductIdGreaterThanOrderByProductIdAsc(long productId, Pageable pageable);
}
