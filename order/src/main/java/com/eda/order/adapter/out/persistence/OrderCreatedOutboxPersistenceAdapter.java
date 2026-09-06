package com.eda.order.adapter.out.persistence;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.order.application.port.out.LoadPendingOrderCreatedOutboxPort;
import com.eda.order.application.port.out.MarkOrderCreatedOutboxPublishedPort;
import com.eda.order.application.port.out.SaveOrderCreatedOutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderCreatedOutboxPersistenceAdapter implements
        SaveOrderCreatedOutboxPort,
        LoadPendingOrderCreatedOutboxPort,
        MarkOrderCreatedOutboxPublishedPort {

    private final OrderCreatedOutboxJpaRepository orderCreatedOutboxJpaRepository;

    @Override
    public void save(OrderCreatedEvent event) {
        OrderCreatedOutboxJpaEntity entity = OrderCreatedOutboxJpaEntity.create(event);
        orderCreatedOutboxJpaRepository.save(entity);
    }

    @Override
    public List<OrderCreatedEvent> loadPending() {
        return orderCreatedOutboxJpaRepository
                .findByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING)
                .stream()
                .map(entity -> {
                    List<OrderCreatedEvent.Line> lines =
                    entity.getLines().stream()
                            .map(line -> new OrderCreatedEvent.Line(
                                    line.getProductId(),
                                    line.getQuantity()
                            ))
                            .toList();
                    return new OrderCreatedEvent(
                            entity.getEventId(),
                            entity.getOrderId(),
                            lines,
                            entity.getOccurredAt()
                    );
                })
                .toList();
    }

    @Override
    public void markPublished(UUID eventId) {
        OrderCreatedOutboxJpaEntity entity =
                orderCreatedOutboxJpaRepository.findById(eventId)
                        .orElseThrow();

        entity.markPublished();
    }
}
