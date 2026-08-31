package com.eda.stock.adapter.out.persistence;

import com.eda.stock.domain.inbox.InboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InboxEventJpaRepository extends JpaRepository<InboxEvent, UUID> {
    boolean existsByEventIdOrEventTypeAndAggregateId(UUID eventId, String eventType, String aggregateId);
}
