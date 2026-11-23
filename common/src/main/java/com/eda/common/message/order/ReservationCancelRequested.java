package com.eda.common.message.order;

import java.util.UUID;

// 주문을 만들지 못해 재고 예약을 취소해야 함. Try 결과를 모르는 경우에도 보낸다 (예약이 없으면 stock 이 취소 표식만 남김)
public record ReservationCancelRequested(UUID reservationId) {
}
