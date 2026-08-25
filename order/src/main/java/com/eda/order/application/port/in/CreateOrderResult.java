package com.eda.order.application.port.in;

import com.eda.order.domain.OrderStatus;

import java.math.BigDecimal;

public record CreateOrderResult(
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount
) {
}
