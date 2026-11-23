package com.eda.order.api;

import com.eda.common.response.ApiResponse;
import com.eda.order.api.dto.request.CreateOrderRequest;
import com.eda.order.api.dto.response.CreateOrderResponse;
import com.eda.order.application.OrderService;
import com.eda.order.domain.Order;
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
    public ApiResponse<CreateOrderResponse> create(
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        Order order = orderService.create(memberId, request.toOrderItems());
        return ApiResponse.ok(CreateOrderResponse.from(order));
    }
}
