package com.eda.product.adapter.out.persistence;

import com.eda.product.application.port.out.OutboxEventPort;
import com.eda.product.domain.outbox.OutboxEvent;
import com.eda.product.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventPersistenceAdapter implements OutboxEventPort {

    private final OutboxEventJpaRepository repository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return repository.save(outboxEvent);
    }

    @Override
    public List<OutboxEvent> findPending() {
        return repository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING);
    }
}
