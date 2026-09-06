package com.eda.stock.adapter.out.persistence;

import com.eda.common.event.StockDeductionResultEvent;
import com.eda.stock.application.port.out.SaveStockResultOutboxPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockResultOutboxPersistenceAdapter implements SaveStockResultOutboxPort {

    private final StockDeductionResultOutboxJpaRepository repository;

    @Override
    public void save(StockDeductionResultEvent event) {
        StockDeductionResultOutboxJpaEntity entity = StockDeductionResultOutboxJpaEntity.create(event);
        repository.save(entity);
    }
}
