package com.eda.common.response;

import com.eda.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

public record ErrorResponse(
        boolean success,
        String errorCode,
        String message,
        Map<String, String> errors
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(false, errorCode.name(), errorCode.getMessage(), null);
    }

    /**
     * 도메인 규칙 위반이 아닌 HTTP 프로토콜 수준 오류(405, 415 등)용.
     * 상태코드 이름을 그대로 errorCode 로 쓴다.
     */
    public static ErrorResponse of(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String code = (status != null) ? status.name() : "HTTP_" + statusCode.value();
        String message = statusCode.is5xxServerError() ? "서버 오류가 발생했습니다" : "요청이 올바르지 않습니다";
        return new ErrorResponse(false, code, message, null);
    }

    public static ErrorResponse validationError(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return new ErrorResponse(false, ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.getMessage(), fieldErrors);
    }
}
