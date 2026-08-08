package com.eda.stock.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.stock.adapter.in.web.dto.request.StockAddRequest;
import com.eda.stock.adapter.in.web.dto.response.StockAddResponse;
import com.eda.stock.application.port.in.StockAddResult;
import com.eda.stock.application.port.in.AddStockUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final AddStockUseCase addStockUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<StockAddResponse>> add(@RequestBody @Valid StockAddRequest request) {
        StockAddResult result = addStockUseCase.add(request.productId(), request.quantity());
        return ResponseEntity.ok(ApiResponse.ok(StockAddResponse.from(result)));
    }
}
