package com.eda.order.adapter.out.persistence.outbox;

import com.eda.order.application.port.out.LoadPendingOrderOutboxEventPort;
import com.eda.order.application.port.out.SaveOrderOutboxEventPort;
import com.eda.order.domain.outbox.OrderOutboxEvent;
import com.eda.order.domain.outbox.OrderOutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderOutboxPersistenceAdapter
        implements SaveOrderOutboxEventPort, LoadPendingOrderOutboxEventPort {

    private final OrderOutboxEventRepository repository;

    @Override
    public OrderOutboxEvent save(OrderOutboxEvent outboxEvent) {
        return repository.save(outboxEvent);
    }

    @Override
    public List<OrderOutboxEvent> loadPendingEvents() {
        return repository.findAllByStatusOrderByOccurredAtAsc(OrderOutboxStatus.PENDING);
    }
}