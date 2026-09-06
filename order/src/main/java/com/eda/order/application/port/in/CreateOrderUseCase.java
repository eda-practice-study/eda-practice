package com.eda.order.application.port.in;

import com.eda.order.application.port.in.dto.CreateOrderResponse;
import com.eda.order.application.port.in.command.CreateOrderCommand;

public interface CreateOrderUseCase {

    CreateOrderResponse create(CreateOrderCommand createOrderCommand);
}
