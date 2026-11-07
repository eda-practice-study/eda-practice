package com.eda.product.controller;

import com.eda.common.exception.ErrorCode;
import com.eda.common.response.ApiResponse;
import com.eda.common.response.ErrorResponse;
import com.eda.product.dto.CreateProductRequest;
import com.eda.product.dto.CreateProductResponse;
import com.eda.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.ok(productService.create(request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
    }
}
