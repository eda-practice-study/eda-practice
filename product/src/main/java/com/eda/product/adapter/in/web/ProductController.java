package com.eda.product.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.product.adapter.in.web.dto.request.ProductCreateRequest;
import com.eda.product.adapter.in.web.dto.response.ProductCreateResponse;
import com.eda.product.application.port.in.CreateProductUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final CreateProductUseCase createProductUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductCreateResponse>> create(@RequestBody @Valid ProductCreateRequest request) {
        Long productId = createProductUseCase.create(request.productName(), request.price());
        return ResponseEntity.ok().body(ApiResponse.ok(new ProductCreateResponse(productId)));
    }
}
