package com.eda.stock.api;

import com.eda.common.response.ApiResponse;
import com.eda.stock.api.dto.IncreaseStockRequest;
import com.eda.stock.api.dto.StockResponse;
import com.eda.stock.application.StockService;
import com.eda.stock.domain.Stock;
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

    private final StockService stockService;

    @PostMapping("/{productId}/increase")
    public ApiResponse<StockResponse> increase(
            @PathVariable Long productId,
            @Valid @RequestBody IncreaseStockRequest request
    ) {
        Stock stock = stockService.increase(productId, request.quantity());
        return ApiResponse.ok(StockResponse.of(stock, null));
    }
}
