package com.eda.product.exception;

import com.eda.common.exception.ErrorCode;
import com.eda.common.response.ErrorResponse;
import com.eda.product.api.ProductController;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ProductController.class)
public class ProductControllerAdvice {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "uk_product_name".equals(violation.getConstraintName())) {
                log.warn("Product name unique constraint violation", ex);
                return ResponseEntity.status(ErrorCode.PRODUCT_NAME_DUPLICATED.getStatus())
                        .body(ErrorResponse.of(ErrorCode.PRODUCT_NAME_DUPLICATED));
            }
        }

        log.error("Unexpected product data integrity violation", ex);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }
}
