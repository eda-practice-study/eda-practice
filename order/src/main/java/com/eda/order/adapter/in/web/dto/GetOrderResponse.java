package com.eda.order.adapter.in.web.dto;

import com.eda.order.application.port.in.GetOrderResult;
import com.eda.order.domain.OrderCancelReason;
import com.eda.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record GetOrderResponse(
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount,
        OrderCancelReason cancelReason,
        List<LineResponse> lines
) {
    public static GetOrderResponse from(GetOrderResult result) {
        List<LineResponse> lines = result.lines().stream()
                .map(line -> new LineResponse(
                        line.productId(),
                        line.productName(),
                        line.unitPrice(),
                        line.quantity(),
                        line.subtotal()
                ))
                .toList();

        return new GetOrderResponse(
                result.orderId(),
                result.status(),
                result.totalAmount(),
                result.cancelReason(),
                lines
        );
    }

    public record LineResponse(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal
    ) {}
}
