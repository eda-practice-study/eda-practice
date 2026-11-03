package com.eda.product.api;

import com.eda.common.response.ApiResponse;
import com.eda.product.api.dto.CreateProductRequest;
import com.eda.product.api.dto.CreateProductResponse;
import com.eda.product.application.ProductService;
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
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateProductResponse>> create(
            @Valid @RequestBody CreateProductRequest request
            ) {
        Product product = productService.create(
                request.name(),
                request.price()
        );

        CreateProductResponse response = new CreateProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice().longValueExact(),
                product.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }


}
