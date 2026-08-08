package com.eda.common.response;

import com.eda.common.exception.ErrorCode;
import org.springframework.web.bind.MethodArgumentNotValidException;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "success", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<ErrorDetail> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.getMessage(), ErrorDetail.of(errorCode));
    }

    public static ApiResponse<ErrorDetail> validationError(MethodArgumentNotValidException ex) {
        return new ApiResponse<>(false, ErrorCode.VALIDATION_ERROR.getMessage(), ErrorDetail.validationError(ex));
    }
}
