package com.eda.stock.api;

import com.eda.common.response.ApiResponse;
import com.eda.stock.application.StockIncreaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockIncreaseService stockIncreaseService;

    @PostMapping("/{productId}/increase")
    public ApiResponse<StockIncreaseResponse> increase(
            @PathVariable Long productId, @Valid @RequestBody IncreaseStockRequest request) {
        return ApiResponse.ok(StockIncreaseResponse.from(stockIncreaseService.increase(productId, request.quantity())));
    }
}
