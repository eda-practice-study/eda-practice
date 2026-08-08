package com.eda.common.exception;

import com.eda.common.response.ApiResponse;
import com.eda.common.response.ErrorDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 공통 예외 처리기
 * 웹 서비스의 @SpringBootApplication 에 @Import(GlobalExceptionHandler.class) 로 등록한다.
 * (starter-web 은 common 에서 compileOnly 이므로 실제 web 은 각 웹 서비스가 런타임에 제공한다.)
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<ErrorDetail>> handleBusiness(BaseException ex) {
        log.warn("Business exception: {}", ex.getErrorCode());
        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(ApiResponse.error(ex.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<ErrorDetail>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.validationError(ex));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<ErrorDetail>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing request header: {}", ex.getHeaderName());
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<ErrorDetail>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<ErrorDetail>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No handler for {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus()).body(ApiResponse.error(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<ErrorDetail>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMethod());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<ErrorDetail>> handleException(Exception ex) {
        log.error("Unexpected exception", ex);
        return ResponseEntity.internalServerError().body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }
}
