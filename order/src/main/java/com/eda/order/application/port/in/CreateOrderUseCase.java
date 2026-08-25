package com.eda.order.application.port.in;

public interface CreateOrderUseCase {
    CreateOrderResult create(CreateOrderCommand command);
}
