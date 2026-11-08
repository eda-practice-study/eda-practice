package com.eda.stock.exception;

import com.eda.common.exception.ErrorCode;
import com.eda.common.response.ErrorResponse;
import com.eda.stock.api.StockController;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = StockController.class)
public class StockControllerAdvice {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "request_history_pkey".equals(violation.getConstraintName())) {
                return ResponseEntity.status(ErrorCode.DUPLICATE_STOCK_INCREASE_REQUEST.getStatus())
                        .body(ErrorResponse.of(ErrorCode.DUPLICATE_STOCK_INCREASE_REQUEST));
            }
        }
        log.error("Unexpected stock data integrity violation", ex);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleStockConflict(OptimisticLockingFailureException ex) {
        return ResponseEntity.status(ErrorCode.STOCK_INCREASE_CONFLICT.getStatus())
                .body(ErrorResponse.of(ErrorCode.STOCK_INCREASE_CONFLICT));
    }
}
