package com.eda.order.application.port.out;

import com.eda.order.domain.outbox.OrderOutboxEvent;

import java.util.List;

public interface LoadPendingOrderOutboxEventPort {

    List<OrderOutboxEvent> loadPendingEvents();
}