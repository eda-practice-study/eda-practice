package com.eda.common.exception;

import com.eda.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 공통 예외 처리기
 * 웹 서비스의 @SpringBootApplication 에 @Import(GlobalExceptionHandler.class) 로 등록한다.
 * (starter-web 은 common 에서 compileOnly 이므로 실제 web 은 각 웹 서비스가 런타임에 제공한다.)
 *
 * ResponseEntityExceptionHandler 를 상속해 405/415/404 같은 표준 MVC 예외가
 * 상태코드를 유지한 채 공통 응답 포맷으로 나가게 한다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BaseException ex) {
        log.warn("Business exception: {}", ex.getErrorCode());
        return ResponseEntity.status(ex.getErrorCode().getStatus()).body(ErrorResponse.of(ex.getErrorCode()));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing request header: {}", ex.getHeaderName());
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unexpected exception", ex);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * 부모가 이미 이 예외를 매핑하고 있어 별도 @ExceptionHandler 로 두면 매핑이 충돌한다.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.validationError(ex));
    }

    /**
     * 부모가 처리하는 표준 MVC 예외의 최종 응답 지점.
     * 상태코드는 Spring 이 정한 값을 유지하고 본문만 공통 포맷으로 바꾼다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        log.warn("MVC exception: {} -> {}", ex.getClass().getSimpleName(), statusCode);
        return ResponseEntity.status(statusCode).headers(headers).body(ErrorResponse.of(statusCode));
    }
}
