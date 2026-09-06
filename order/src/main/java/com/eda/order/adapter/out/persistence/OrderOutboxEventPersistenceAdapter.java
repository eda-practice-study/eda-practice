package com.eda.order.adapter.out.persistence;

import com.eda.order.application.port.out.LoadOrderOutboxEventPort;
import com.eda.order.application.port.out.SaveOrderOutboxEventPort;
import com.eda.order.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrderOutboxEventPersistenceAdapter implements SaveOrderOutboxEventPort, LoadOrderOutboxEventPort {

    private static final int POLL_BATCH_SIZE = 100;

    private final OrderOutboxEventJpaRepository outboxEventRepository;

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
