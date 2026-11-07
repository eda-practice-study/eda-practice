package com.eda.stock.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockIncreaseRequestRepository extends JpaRepository<StockIncreaseRequestHistory, Long> {
    Optional<StockIncreaseRequestHistory> findByIdempotencyKey(String idempotencyKey);
}
