package com.eda.order.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderCreatedOutboxJpaRepository extends JpaRepository<OrderCreatedOutboxJpaEntity, UUID> {

    List<OrderCreatedOutboxJpaEntity>
    findByStatusOrderByOccurredAtAsc(OutboxStatus status);
}
