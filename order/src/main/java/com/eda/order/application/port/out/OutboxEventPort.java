package com.eda.order.application.port.out;

import com.eda.order.domain.outbox.OutboxEvent;

import java.util.List;

public interface OutboxEventPort {
    OutboxEvent save(OutboxEvent outboxEvent);
    List<OutboxEvent> findPending();
}
