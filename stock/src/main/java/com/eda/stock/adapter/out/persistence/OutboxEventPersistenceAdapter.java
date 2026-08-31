package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.OutboxEventPort;
import com.eda.stock.domain.outbox.OutboxEvent;
import com.eda.stock.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventPersistenceAdapter implements OutboxEventPort {

    private final OutboxEventJpaRepository repository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return repository.saveAndFlush(event);
    }

    @Override
    public List<OutboxEvent> findPending() {
        return repository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING);
    }
}
