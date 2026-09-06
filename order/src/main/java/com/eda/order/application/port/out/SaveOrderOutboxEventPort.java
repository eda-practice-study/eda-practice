package com.eda.order.application.port.out;

import com.eda.order.domain.OutboxEvent;

public interface SaveOrderOutboxEventPort {

    OutboxEvent save(OutboxEvent outboxEvent);
}
