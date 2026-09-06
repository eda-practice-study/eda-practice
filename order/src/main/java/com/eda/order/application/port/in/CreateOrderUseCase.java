package com.eda.order.application.port.in;

import com.eda.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.util.List;

public interface CreateOrderUseCase {

    OrderResult create(Long memberId, List<LineRequest> lines);

    record LineRequest(Long productId, int quantity) {
    }

    record OrderResult(Long orderId, OrderStatus status, BigDecimal totalAmount) {
    }
}
