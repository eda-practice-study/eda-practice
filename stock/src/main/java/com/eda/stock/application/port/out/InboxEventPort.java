package com.eda.stock.application.port.out;

import com.eda.stock.domain.inbox.InboxEvent;

import java.util.UUID;

public interface InboxEventPort {
    boolean exists(
            UUID eventId,
            String eventType,
            String aggregateId
    );

    InboxEvent save(InboxEvent inboxEvent);
}
