package com.eda.product.adapter.out.persistence.outbox;

import com.eda.product.application.port.out.SaveOutboxEventPort;
import com.eda.product.domain.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements SaveOutboxEventPort {

    private final OutboxEventRepository outboxEventRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventRepository.save(outboxEvent);
    }
}
