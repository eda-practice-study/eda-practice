package com.eda.product.application.port.out;

import com.eda.product.domain.outbox.OutboxEvent;

import java.util.List;

public interface LoadPendingOutboxEventPort {

    List<OutboxEvent> loadPendingEvents();
}
