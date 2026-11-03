package com.eda.stock.api;

import com.eda.common.response.ApiResponse;
import com.eda.stock.api.dto.IncreaseStockRequest;
import com.eda.stock.api.dto.IncreaseStockResponse;
import com.eda.stock.application.StockService;
import com.eda.stock.domain.Stock;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService service;

    @PostMapping("/{productId}/increase")
    public ResponseEntity<ApiResponse<IncreaseStockResponse>> increase(
            @PathVariable Long productId,
            @Valid @RequestBody IncreaseStockRequest request
            ) {
        Stock stock = service.increase(productId, request.quantity());

        Long availableQuantity = service.increaseAvailable(
                productId,
                request.quantity()
        );

        IncreaseStockResponse response = new IncreaseStockResponse(
                stock.getProductId(),
                stock.getQuantity(),
                availableQuantity
        );

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
