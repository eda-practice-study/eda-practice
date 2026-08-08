package com.eda.product.adapter.out.persistence;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.out.SaveOutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements SaveOutboxPort {

    private final ProductCreatedOutboxJpaRepository productCreatedOutboxJpaRepository;

    @Override
    public void save(ProductCreatedEvent event) {
        ProductCreatedOutboxJpaEntity entity = ProductCreatedOutboxJpaEntity.create(
                event.eventId(),
                event.productId(),
                event.occurredAt()
        );

        productCreatedOutboxJpaRepository.save(entity);

    }
}
