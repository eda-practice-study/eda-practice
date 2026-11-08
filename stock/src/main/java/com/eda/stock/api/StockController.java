package com.eda.stock.api;

import com.eda.common.response.ApiResponse;
import com.eda.stock.api.dto.IncreaseStockRequest;
import com.eda.stock.api.dto.IncreaseStockResponse;
import com.eda.stock.application.StockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class StockController {

    private final StockService stockService;

    @PostMapping("/{productId}/increase")
    public ResponseEntity<ApiResponse<IncreaseStockResponse>> increaseStock(@PathVariable(value = "productId") Long productId,
                                                                            @Valid @RequestBody IncreaseStockRequest request) {

        IncreaseStockResponse response = stockService.increaseStock(productId, request.quantity());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.ok(response));
    }
}
