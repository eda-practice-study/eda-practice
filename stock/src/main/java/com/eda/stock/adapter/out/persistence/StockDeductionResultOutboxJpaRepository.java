package com.eda.stock.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockDeductionResultOutboxJpaRepository extends JpaRepository<StockDeductionResultOutboxJpaEntity, UUID> {

    List<StockDeductionResultOutboxJpaEntity>
    findByStatusOrderByOccurredAtAsc(OutboxStatus status);
}
