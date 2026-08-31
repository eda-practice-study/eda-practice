package com.eda.order.adapter.in.web.dto;

import com.eda.order.application.port.in.CreateOrderResult;
import com.eda.order.domain.OrderStatus;

import java.math.BigDecimal;

public record CreateOrderResponse(
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount
) {
    public static CreateOrderResponse from(CreateOrderResult result) {
        return new CreateOrderResponse(
                result.orderId(),
                result.status(),
                result.totalAmount()
        );
    }
}
