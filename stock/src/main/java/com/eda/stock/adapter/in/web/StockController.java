package com.eda.stock.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.stock.application.port.in.AddStockUseCase;
import com.eda.stock.domain.Stock;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class StockController {
    private final AddStockUseCase addStockUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<StockResponse>> addStock(
            @Valid @RequestBody AddStockRequest request
    ) {
        Stock stock = addStockUseCase.add(request.toCommand());

        return ResponseEntity.ok(
                ApiResponse.ok(
                        "재고가 추가되었습니다.",
                        StockResponse.from(stock)
                )
        );
    }
}
