package com.eda.common.response;

import com.eda.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

public record ErrorDetail(
        String code,
        Map<String, String> fieldErrors
) {

    public static ErrorDetail of(ErrorCode errorCode) {
        return new ErrorDetail(errorCode.name(), null);
    }

    public static ErrorDetail validationError(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return new ErrorDetail(ErrorCode.VALIDATION_ERROR.name(), fieldErrors);
    }
}
