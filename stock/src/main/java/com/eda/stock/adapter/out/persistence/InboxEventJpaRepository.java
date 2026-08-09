package com.eda.stock.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventJpaRepository
        extends JpaRepository<InboxEventJpaEntity, UUID> {
    boolean existsByEventId(UUID eventId);
}
