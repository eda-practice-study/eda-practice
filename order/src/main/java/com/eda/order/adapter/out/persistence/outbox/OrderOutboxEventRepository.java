package com.eda.order.adapter.out.persistence.outbox;

import com.eda.order.domain.outbox.OrderOutboxEvent;
import com.eda.order.domain.outbox.OrderOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderOutboxEventRepository extends JpaRepository<OrderOutboxEvent, UUID> {
    List<OrderOutboxEvent> findAllByStatusOrderByOccurredAtAsc(OrderOutboxStatus status);
}
