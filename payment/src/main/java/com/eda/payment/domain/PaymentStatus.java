package com.eda.payment.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentStatus {

    PENDING("결제대기", "결제 시도 생성, 승인 대기"),
    PAID("결제완료", "결제 승인 완료"),
    FAILED("결제실패", "결제 승인 실패"),
    PARTIALLY_REFUNDED("부분환불", "일부 금액 환불됨"),
    REFUNDED("전액환불", "전액 환불됨");

    private final String displayName;
    private final String description;
}
