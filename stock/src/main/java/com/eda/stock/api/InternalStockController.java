package com.eda.stock.api;

import com.eda.common.response.ApiResponse;
import com.eda.stock.application.AvailableStockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/stocks")
@RequiredArgsConstructor
public class InternalStockController {

    private final AvailableStockService availableStockService;

    @PostMapping("/decrease")
    public ApiResponse<Void> decrease(@Valid @RequestBody DecreaseStockRequest request) {
        availableStockService.decrease(request.orderKey().toString(), request.toItems());
        return ApiResponse.ok(null);
    }

    @PostMapping("/restore")
    public ApiResponse<Void> restore(@Valid @RequestBody RestoreStockRequest request) {
        availableStockService.restore(request.orderKey().toString());
        return ApiResponse.ok(null);
    }
}
