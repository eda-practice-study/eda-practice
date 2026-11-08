package com.eda.product.api;

import com.eda.common.response.ApiResponse;
import com.eda.product.api.dto.CreateProductRequest;
import com.eda.product.api.dto.CreateProductResponse;
import com.eda.product.application.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    // 상품 생성 api
    @PostMapping
    ResponseEntity<ApiResponse<CreateProductResponse>> createProduct(@Valid @RequestBody CreateProductRequest request) {

        CreateProductResponse response = productService.createProduct(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }
}
