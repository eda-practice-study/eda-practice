package com.eda.order.application.port.in;

import com.eda.order.domain.OrderCancelReason;
import com.eda.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record GetOrderResult(
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount,
        OrderCancelReason cancelReason,
        List<Line> lines
) {
    public record Line(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal
    ) {}
}
