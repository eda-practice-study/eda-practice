package com.eda.stock.application.port.out;

import com.eda.stock.domain.outbox.OutboxEvent;

import java.util.List;

public interface OutboxEventPort {
    OutboxEvent save(OutboxEvent event);
    List<OutboxEvent> findPending();
}
