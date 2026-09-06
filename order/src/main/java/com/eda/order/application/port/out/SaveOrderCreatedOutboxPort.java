package com.eda.order.application.port.out;

import com.eda.common.event.OrderCreatedEvent;

public interface SaveOrderCreatedOutboxPort {
    void save(OrderCreatedEvent event);
}
