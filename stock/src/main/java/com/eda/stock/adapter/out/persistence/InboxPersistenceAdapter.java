package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.InboxEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InboxPersistenceAdapter implements InboxEventPort {

    private final InboxEventJpaRepository inboxEventJpaRepository;

    @Override
    public boolean existByEventId(UUID eventId) {
        return inboxEventJpaRepository.existsByEventId(eventId);
    }

    @Override
    public void save(UUID eventId, String eventType) {
        InboxEventJpaEntity inboxEvent = InboxEventJpaEntity.create(eventId, eventType);

        inboxEventJpaRepository.save(inboxEvent);
    }
}
