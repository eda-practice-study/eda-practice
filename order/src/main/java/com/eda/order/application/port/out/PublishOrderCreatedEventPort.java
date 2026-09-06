package com.eda.order.application.port.out;

import com.eda.common.event.OrderCreatedEvent;

public interface PublishOrderCreatedEventPort {

    void publish(OrderCreatedEvent event);
}
