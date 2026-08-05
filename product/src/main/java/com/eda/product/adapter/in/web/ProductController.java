package com.eda.product.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.domain.Product;
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
    private final CreateProductUseCase createProductUsecase;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        Product product = createProductUsecase.create(request.toCommand());

        ProductResponse response = ProductResponse.from(product);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(
                        "상품이 등록되었습니다.",
                        response
                ));
    }
}
