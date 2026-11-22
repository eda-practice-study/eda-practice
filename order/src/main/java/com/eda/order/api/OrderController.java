package com.eda.order.api;

import com.eda.common.response.ApiResponse;
import com.eda.order.application.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(
            @RequestHeader("X-Member-Id") Long memberId, @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(OrderResponse.from(orderService.create(memberId, request.items().stream()
                .map(item -> new OrderService.Item(item.productId(), item.quantity()))
                .toList())));
    }
}
