package com.eda.order.application.port.in;

import com.eda.order.domain.Order;

public interface CreateOrderUseCase {

    Order create(CreateOrderCommand command);
}
