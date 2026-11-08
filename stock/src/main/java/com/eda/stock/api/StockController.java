package com.eda.stock.api;

import com.eda.common.response.ApiResponse;
import com.eda.stock.service.StockIncreaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockIncreaseService stockIncreaseService;

    @PostMapping("/{productId}/increase")
    public ApiResponse<StockIncreaseResponse> increase(
            @PathVariable Long productId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody StockIncreaseRequest request) {
        return ApiResponse.ok(stockIncreaseService.increaseForRequest(idempotencyKey, productId, request.quantity()));
    }
}
