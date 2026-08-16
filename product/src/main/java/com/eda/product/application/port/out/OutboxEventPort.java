package com.eda.product.application.port.out;

import com.eda.product.domain.outbox.OutboxEvent;

import java.util.List;

public interface OutboxEventPort {
    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findPending();
}
