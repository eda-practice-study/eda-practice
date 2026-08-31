package com.eda.product.adapter.in.web.controller;

import com.eda.common.response.ApiResponse;
import com.eda.product.adapter.in.web.dto.CreateProductRequest;
import com.eda.product.adapter.in.web.dto.CreateProductResponse;
import com.eda.product.application.port.in.CreateProductUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final CreateProductUseCase createProductUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateProductResponse>> create(
            @Valid @RequestBody CreateProductRequest request
    ) {
        Long productId = createProductUseCase.create(request.toCommand());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(new CreateProductResponse(productId)));
    }
}
