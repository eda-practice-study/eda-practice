package com.eda.order.adapter.in.web.dto.response;

import com.eda.order.domain.OrderStatus;
import java.math.BigDecimal;

public record OrderCreateResponse(
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount
) {
}
