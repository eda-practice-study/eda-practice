package com.eda.order.application.port.in;

public interface GetOrderUseCase {
    GetOrderResult get(Long orderId);
}
