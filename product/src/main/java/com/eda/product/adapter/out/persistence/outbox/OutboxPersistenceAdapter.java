package com.eda.product.adapter.out.persistence.outbox;

import com.eda.product.application.port.out.LoadPendingOutboxEventPort;
import com.eda.product.application.port.out.SaveOutboxEventPort;
import com.eda.product.domain.outbox.OutboxEvent;
import com.eda.product.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements SaveOutboxEventPort, LoadPendingOutboxEventPort {

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventRepository.save(outboxEvent);
    }

    @Override
    public List<OutboxEvent> loadPendingEvents() {
        return outboxEventRepository.findAllByStatusOrderByOccurredAtAsc(OutboxStatus.PENDING);
    }
}
