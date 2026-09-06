package com.eda.order.application.port.out;

import com.eda.common.event.OrderCreatedEvent;

import java.util.List;

public interface LoadPendingOrderCreatedOutboxPort {

    List<OrderCreatedEvent> loadPending();
}
