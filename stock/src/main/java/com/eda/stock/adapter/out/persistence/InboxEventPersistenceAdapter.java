package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.InboxEventPort;
import com.eda.stock.domain.inbox.InboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class InboxEventPersistenceAdapter implements InboxEventPort {

    private final InboxEventJpaRepository repository;

    @Override
    public boolean exists(UUID eventId, String eventType, String aggregateId) {
        return repository.existsByEventIdOrEventTypeAndAggregateId(eventId, eventType, aggregateId);
    }

    // 즉시 flush하는 이유는 중복 unique 충돌을 재고 차감 전에 DB에서 확인하기 위해서
    @Override
    public InboxEvent save(InboxEvent inboxEvent) {
        return repository.saveAndFlush(inboxEvent);
    }
}
