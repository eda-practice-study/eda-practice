package com.eda.stock.controller;

import com.eda.common.exception.ErrorCode;
import com.eda.common.response.ApiResponse;
import com.eda.common.response.ErrorResponse;
import com.eda.stock.event.StockSnapshot;
import com.eda.stock.redis.StockCache;
import com.eda.stock.service.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stocks/{productId}")
@RequiredArgsConstructor
public class StockController {

    private final StockService stocks;
    private final StockCache cache;

    public record IncreaseRequest(@NotNull @Min(1) @Max(10_000) @Digits(integer = 5, fraction = 0) BigDecimal quantity) {
    }

    public record IncreaseResponse(Long productId, int quantity, Integer availableQuantity) {
    }

    public record StockResponse(Long productId, int quantity, Integer availableQuantity, boolean consistent) {
    }

    @PostMapping("/increase")
    public ApiResponse<IncreaseResponse> increase(@PathVariable Long productId, @Valid @RequestBody IncreaseRequest request) {
        StockSnapshot saved = stocks.increase(productId, request.quantity().intValueExact());
        StockSnapshot cached = refreshCache(saved);
        return ApiResponse.ok(new IncreaseResponse(productId, saved.quantity(),
                cached == null ? null : cached.quantity()));
    }

    @GetMapping
    public ApiResponse<StockResponse> find(@PathVariable Long productId) {
        StockSnapshot saved = stocks.find(productId);
        StockSnapshot cached = findCached(saved);
        return ApiResponse.ok(new StockResponse(productId, saved.quantity(),
                cached == null ? null : cached.quantity(), saved.equals(cached)));
    }

    private StockSnapshot refreshCache(StockSnapshot saved) {
        try {
            cache.apply(saved);
            return cache.find(saved.productId());
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private StockSnapshot findCached(StockSnapshot saved) {
        try {
            StockSnapshot cached = cache.find(saved.productId());
            if (cached == null || cached.version() < saved.version()) {
                cache.apply(saved);
                return cache.find(saved.productId());
            }
            return cached;
        } catch (DataAccessException ex) {
            return null;
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody() {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR));
    }
}
