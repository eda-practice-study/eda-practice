package com.eda.stock.adapter.out.persistence;

import com.eda.common.event.StockDeductionResultEvent;
import com.eda.stock.application.port.out.LoadPendingStockResultOutboxPort;
import com.eda.stock.application.port.out.MarkStockResultOutboxPublishedPort;
import com.eda.stock.application.port.out.SaveStockResultOutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StockResultOutboxPersistenceAdapter implements
        SaveStockResultOutboxPort,
        LoadPendingStockResultOutboxPort,
        MarkStockResultOutboxPublishedPort {

    private final StockDeductionResultOutboxJpaRepository repository;

    @Override
    public void save(StockDeductionResultEvent event) {
        StockDeductionResultOutboxJpaEntity entity = StockDeductionResultOutboxJpaEntity.create(event);
        repository.save(entity);
    }

    @Override
    public List<StockDeductionResultEvent> loadPending() {
        return repository
                .findByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING)
                .stream()
                .map(entity -> new StockDeductionResultEvent(
                        entity.getEventId(),
                        entity.getOrderId(),
                        entity.getResult(),
                        entity.getOccurredAt()
                ))
                .toList();
    }

    @Override
    public void markPublished(UUID eventId) {
        StockDeductionResultOutboxJpaEntity entity =
                repository.findById(eventId)
                        .orElseThrow();

        entity.markPublished();
    }
}
