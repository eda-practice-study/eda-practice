package com.eda.product.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.product.adapter.in.web.dto.CreateProductRequest;
import com.eda.product.application.port.in.CreateProductUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final CreateProductUseCase createProductUseCase;

    @PostMapping("/admin/products")
    public ResponseEntity<ApiResponse<String>> createProduct(@RequestBody CreateProductRequest createProductRequest) {

        createProductUseCase.register(createProductRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("상품 등록 성공"));
    }
}
