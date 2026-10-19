package com.eda.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"),

    PRODUCT_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 등록된 상품명입니다"),

    OUT_OF_STOCK(HttpStatus.CONFLICT, "재고가 부족합니다"),
    INVALID_STOCK_QUANTITY(HttpStatus.BAD_REQUEST, "재고 수량이 올바르지 않습니다");

    private final HttpStatus status;
    private final String message;
}
