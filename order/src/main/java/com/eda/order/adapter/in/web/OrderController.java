package com.eda.order.adapter.in.web;

import com.eda.common.response.ApiResponse;
import com.eda.order.adapter.in.web.dto.request.OrderCreateRequest;
import com.eda.order.adapter.in.web.dto.request.OrderLineRequest;
import com.eda.order.adapter.in.web.dto.response.OrderCreateResponse;
import com.eda.order.adapter.in.web.dto.response.OrderResponse;
import com.eda.order.application.port.in.CreateOrderUseCase;
import com.eda.order.application.port.in.GetOrderUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderCreateResponse>> create(
            @RequestHeader("X-Member-Id") Long memberId,
            @RequestBody @Valid OrderCreateRequest request
    ) {
        List<CreateOrderUseCase.LineRequest> lines = request.lines().stream()
                .map(this::toLineRequest)
                .toList();
        CreateOrderUseCase.OrderResult result = createOrderUseCase.create(memberId, lines);
        OrderCreateResponse response = new OrderCreateResponse(
                result.orderId(), result.status(), result.totalAmount()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/orders/" + result.orderId()))
                .body(ApiResponse.ok(response));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@PathVariable Long orderId) {
        GetOrderUseCase.OrderResult result = getOrderUseCase.get(orderId);
        OrderResponse response = new OrderResponse(
                result.orderId(), result.status(), result.totalAmount(), result.cancelReason()
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private CreateOrderUseCase.LineRequest toLineRequest(OrderLineRequest line) {
        return new CreateOrderUseCase.LineRequest(line.productId(), line.quantity());
    }
}
