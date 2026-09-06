package com.eda.order.application.port.in;

import com.eda.order.domain.OrderStatus;
import java.math.BigDecimal;

public interface GetOrderUseCase {

    OrderResult get(Long orderId);

    record OrderResult(Long orderId, OrderStatus status, BigDecimal totalAmount, String cancelReason) {
    }
}
