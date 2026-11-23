package com.eda.common.message.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// reservationId = 주문 때 stock 에 Try 한 재고 예약. stock 은 이 이벤트로 예약을 Confirm 한다
public record OrderCreated(Long orderId, UUID reservationId, Long memberId, List<Item> items, BigDecimal totalAmount) {

    public record Item(Long productId, int quantity) {
    }
}
