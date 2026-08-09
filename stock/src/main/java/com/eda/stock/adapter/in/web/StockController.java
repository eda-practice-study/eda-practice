package com.eda.stock.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.stock.adapter.in.web.dto.AddStockRequest;
import com.eda.stock.application.port.in.AddStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StockController {

    private final AddStockUseCase addStockUseCase;

    @PostMapping("/admin/stocks")
    public ResponseEntity<ApiResponse<String>> createStock(@RequestBody AddStockRequest addStockRequest) {

        addStockUseCase.add(addStockRequest);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("재고 등록 성공"));
    }
}
