package com.eda.order.adapter.in.web.controller;

import com.eda.common.response.ApiResponse;
import com.eda.order.adapter.in.web.dto.CreateOrderRequest;
import com.eda.order.adapter.in.web.dto.CreateOrderResponse;
import com.eda.order.application.port.in.CreateOrderResult;
import com.eda.order.application.port.in.CreateOrderUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> create(
            @RequestHeader("X-Member-Id")
            @Positive
            Long memberId,

            @Valid
            @RequestBody
            CreateOrderRequest request
    ) {
        CreateOrderResult result =
                createOrderUseCase.create(request.toCommand(memberId));

        CreateOrderResponse response =
                CreateOrderResponse.from(result);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(response));
    }
}
