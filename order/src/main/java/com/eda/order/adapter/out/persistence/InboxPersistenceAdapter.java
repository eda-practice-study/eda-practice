package com.eda.order.adapter.out.persistence;

import com.eda.order.application.port.out.InboxEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InboxPersistenceAdapter implements InboxEventPort {

    private final InboxEventJpaRepository inboxEventJpaRepository;

    @Override
    public boolean existsByEventId(UUID eventId) {
        return inboxEventJpaRepository.existsById(eventId);
    }

    @Override
    public void save(UUID eventId, String eventType) {
        inboxEventJpaRepository.save(InboxEventJpaEntity.create(eventId, eventType));
    }
}
