package com.eda.order.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.order.adapter.in.web.dto.CreateOrderRequest;
import com.eda.order.application.port.in.dto.CreateOrderResponse;
import com.eda.order.application.port.in.CreateOrderUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    @PostMapping("/api/orders")
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody CreateOrderRequest createOrderRequest
    ) {

        CreateOrderResponse response = createOrderUseCase.create(
                createOrderRequest.toCommand(memberId)
        );

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(response));
    }
}
