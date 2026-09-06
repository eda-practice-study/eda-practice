package com.eda.order.application.port.out;

import com.eda.order.domain.outbox.OrderOutboxEvent;

public interface SaveOrderOutboxEventPort {

    OrderOutboxEvent save(OrderOutboxEvent outboxEvent);
}