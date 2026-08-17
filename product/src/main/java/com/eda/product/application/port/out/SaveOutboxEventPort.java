package com.eda.product.application.port.out;

import com.eda.product.domain.OutboxEvent;

public interface SaveOutboxEventPort {

    OutboxEvent save(OutboxEvent outboxEvent);
}
