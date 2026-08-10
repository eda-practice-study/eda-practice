package com.eda.product.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.product.adapter.in.web.dto.RegisterProductRequest;
import com.eda.product.adapter.in.web.dto.RegisterProductResponse;
import com.eda.product.application.port.in.RegisterProductUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final RegisterProductUseCase registerProductUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterProductResponse> register(@Valid @RequestBody RegisterProductRequest request) {
        Long productId = registerProductUseCase.register(request.toCommand());
        return ApiResponse.ok(new RegisterProductResponse(productId));
    }
}