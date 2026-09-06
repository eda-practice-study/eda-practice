package com.eda.order.application.port.out;

import com.eda.common.event.OrderCreated;

public interface PublishOrderEventPort {

    void publish(OrderCreated event);
}
