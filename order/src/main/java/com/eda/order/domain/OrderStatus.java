package com.eda.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    CREATED("생성완료", "주문 생성, 재고 예약 대기"),
    STOCK_RESERVED("재고예약", "재고 예약 완료, 결제 대기"),
    PAID("결제완료", "결제 완료, 예약 확정 대기"),
    COMPLETED("주문완료", "정상 완료"),
    CANCELED("취소", "재고 부족 또는 결제 실패로 취소"),
    PARTIALLY_REFUNDED("부분환불", "일부 수량 환불됨"),
    REFUNDED("전액환불", "전량 환불됨");

    private final String displayName;
    private final String description;
}
