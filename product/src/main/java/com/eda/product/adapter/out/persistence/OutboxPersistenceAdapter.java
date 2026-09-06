package com.eda.product.adapter.out.persistence;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.out.LoadPendingOutboxPort;
import com.eda.product.application.port.out.MarkOutboxPublishedPort;
import com.eda.product.application.port.out.SaveOutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements
        SaveOutboxPort,
        LoadPendingOutboxPort,
        MarkOutboxPublishedPort
{

    private final ProductCreatedOutboxJpaRepository productCreatedOutboxJpaRepository;

    @Override
    public void save(ProductCreatedEvent event) {
        ProductCreatedOutboxJpaEntity entity = ProductCreatedOutboxJpaEntity.create(
                event.eventId(),
                event.productId(),
                event.name(),
                event.price(),
                event.occurredAt()
        );

        productCreatedOutboxJpaRepository.save(entity);

    }

    @Override
    public List<ProductCreatedEvent> loadPending() {
        return productCreatedOutboxJpaRepository
                .findByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING)
                .stream()
                .map(entity -> new ProductCreatedEvent(
                        entity.getEventId(),
                        entity.getProductId(),
                        entity.getName(),
                        entity.getPrice(),
                        entity.getOccurredAt()
                ))
                .toList();
    }

    @Override
    public void markPublished(UUID eventId) {
        ProductCreatedOutboxJpaEntity entity =
                productCreatedOutboxJpaRepository
                        .findById(eventId).orElseThrow();

        entity.markPublished();
    }
}
