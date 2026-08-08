package com.eda.product.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductCreatedOutboxJpaRepository
        extends JpaRepository<ProductCreatedOutboxJpaEntity, UUID> {

    List<ProductCreatedOutboxJpaEntity> findByStatusOrderByOccurredAtAsc(OutboxStatus status);
}
