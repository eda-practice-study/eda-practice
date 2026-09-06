package com.eda.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // common
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"),
    OUTBOX_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Outbox 이벤트가 존재하지 않습니다"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문이 존재하지 않습니다"),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "주문 가능한 상품 정보가 없습니다"),

    // stock
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "재고가 존재하지 않습니다"),
    INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "재고가 부족합니다"),
    INVALID_STOCK_OPERATION(HttpStatus.BAD_REQUEST, "재고 처리가 올바르지 않습니다"),

    // order
    INVALID_ORDER_STATUS(HttpStatus.BAD_REQUEST, "허용되지 않는 주문 상태 전이입니다"),
    INVALID_REFUND(HttpStatus.BAD_REQUEST, "환불 요청이 올바르지 않습니다"),

    // payment
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "결제에 실패했습니다"),
    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "허용되지 않는 결제 상태 전이입니다");

    private final HttpStatus status;
    private final String message;
}
