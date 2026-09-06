package com.eda.order.adapter.in.web.dto.response;

import com.eda.order.domain.OrderStatus;
import java.math.BigDecimal;

public record OrderResponse(
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount,
        String cancelReason
) {
}
