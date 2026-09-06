package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.LoadStockOutboxEventPort;
import com.eda.stock.application.port.out.SaveStockOutboxEventPort;
import com.eda.stock.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class StockOutboxEventPersistenceAdapter implements LoadStockOutboxEventPort, SaveStockOutboxEventPort {

    private static final int POLL_BATCH_SIZE = 100;

    private final StockOutboxEventJpaRepository outboxEventRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventRepository.save(outboxEvent);
    }

    @Override
    public List<Long> findUnpublishedIds() {
        return outboxEventRepository.findUnpublishedIds(Limit.of(POLL_BATCH_SIZE));
    }

    @Override
    public Optional<OutboxEvent> findByIdForUpdate(Long id) {
        return outboxEventRepository.findByIdForUpdate(id);
    }
}
