package com.eda.stock.api;

import com.eda.common.response.ApiResponse;
import com.eda.stock.api.dto.ReserveStockRequest;
import com.eda.stock.application.StockService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 서비스 간 호출 전용 API (외부 노출 X). 재고 예약 TCC 의 Try · Cancel (Confirm 은 주문 생성 이벤트로 처리)
@RestController
@RequestMapping("/internal/stocks/reservations")
@RequiredArgsConstructor
public class InternalStockController {

    private final StockService stockService;

    @PostMapping
    public ApiResponse<Void> reserve(@Valid @RequestBody ReserveStockRequest request) {
        stockService.reserve(request.reservationId(), request.toQuantities());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{reservationId}/cancel")
    public ApiResponse<Void> cancel(@PathVariable UUID reservationId) {
        stockService.cancel(reservationId);
        return ApiResponse.ok(null);
    }
}
