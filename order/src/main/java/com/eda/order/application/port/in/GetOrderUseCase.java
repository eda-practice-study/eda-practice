package com.eda.order.application.port.in;

import com.eda.order.domain.Order;

public interface GetOrderUseCase {

    Order get(Long orderId);
}
