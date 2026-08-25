package com.eda.order.adapter.out.persistence;

import com.eda.order.domain.outbox.OutboxEvent;
import com.eda.order.domain.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus status);
}
