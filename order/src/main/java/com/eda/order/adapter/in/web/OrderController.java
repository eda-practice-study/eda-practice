package com.eda.order.adapter.in.web;


import com.eda.order.application.port.in.CreateOrderUseCase;
import com.eda.order.application.port.in.GetOrderUseCase;
import com.eda.order.domain.Order;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestHeader("X-Member-Id") Long memberId,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        Order order = createOrderUseCase.create(request.toCommand(memberId));

        CreateOrderResponse response = CreateOrderResponse.from(order);

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<GetOrderResponse> getOrder(
            @PathVariable Long orderId
    ) {
        Order order = getOrderUseCase.get(orderId);

        return ResponseEntity.ok(
                GetOrderResponse.from(order)
        );
    }
}
