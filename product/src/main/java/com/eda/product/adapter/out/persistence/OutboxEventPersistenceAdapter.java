package com.eda.product.adapter.out.persistence;

import com.eda.product.application.port.out.LoadOutboxEventPort;
import com.eda.product.application.port.out.SaveOutboxEventPort;
import com.eda.product.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OutboxEventPersistenceAdapter implements SaveOutboxEventPort, LoadOutboxEventPort {

    private static final int POLL_BATCH_SIZE = 100;

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return jpaRepository.save(outboxEvent);
    }

    @Override
    public List<Long> findUnpublishedIds() {
        return jpaRepository.findUnpublishedIds(Limit.of(POLL_BATCH_SIZE));
    }

    @Override
    public Optional<OutboxEvent> findByIdForUpdate(Long id) {
        return jpaRepository.findByIdForUpdate(id);
    }
}
