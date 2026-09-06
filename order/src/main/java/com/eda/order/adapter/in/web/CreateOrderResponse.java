package com.eda.order.adapter.in.web;

import com.eda.order.domain.Order;
import com.eda.order.domain.OrderStatus;

import java.math.BigDecimal;

public record CreateOrderResponse(
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount
) {
    public static CreateOrderResponse from(Order order) {
        return new CreateOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount()
        );
    }
}
