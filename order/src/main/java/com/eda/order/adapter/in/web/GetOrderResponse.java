package com.eda.order.adapter.in.web;

import com.eda.order.domain.CancelReason;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderStatus;

import java.math.BigDecimal;

public record GetOrderResponse(
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount,
        CancelReason cancelReason
) {
    public static GetOrderResponse from(Order order) {
        return new GetOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCancelReason()
        );
    }
}
