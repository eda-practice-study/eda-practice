package com.eda.stock.adapter.in.web.controller;

import com.eda.common.response.ApiResponse;
import com.eda.stock.adapter.in.web.dto.AddStockRequest;
import com.eda.stock.adapter.in.web.dto.AddStockResponse;
import com.eda.stock.application.port.in.AddStockUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/stocks")
@RequiredArgsConstructor
public class StockAdminController {

    private final AddStockUseCase addStockUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<AddStockResponse>> add(
            @Valid @RequestBody AddStockRequest request
    ) {
        int quantity = addStockUseCase.add(request.toCommand());

        AddStockResponse response = new AddStockResponse(request.productId(), quantity);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
