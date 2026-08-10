package com.eda.stock.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.stock.adapter.in.web.dto.AddStockRequest;
import com.eda.stock.adapter.in.web.dto.StockResponse;
import com.eda.stock.application.port.in.AddStockUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/stocks")
@RequiredArgsConstructor
public class AdminStockController {

    private final AddStockUseCase addStockUseCase;

    @PostMapping
    public ApiResponse<StockResponse> add(@Valid @RequestBody AddStockRequest request) {
        int quantity = addStockUseCase.add(request.toCommand());
        return ApiResponse.ok(new StockResponse(request.productId(), quantity));
    }
}
