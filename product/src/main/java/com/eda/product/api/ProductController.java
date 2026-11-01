package com.eda.product.api;

import com.eda.common.response.ApiResponse;
import com.eda.product.domain.Product;
import com.eda.product.service.ProductService;
import jakarta.validation.Valid;
import java.net.URI;
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

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductCreateResponse>> create(@Valid @RequestBody ProductCreateRequest request) {
        Product product = productService.register(request.name(), request.price());
        return ResponseEntity.created(URI.create("/api/products/" + product.getId()))
                .body(ApiResponse.ok(ProductCreateResponse.from(product)));
    }
}
