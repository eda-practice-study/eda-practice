package com.eda.order.adapter.in.messaging;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.order.application.port.in.CreateOrderProductCommand;
import com.eda.order.application.port.in.CreateOrderProductUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderProductEventListener {

    private final CreateOrderProductUseCase createOrderProductUseCase;

    @KafkaListener(
            topics = "product.events",
            groupId = "order-service.product"
    )
    public void handle(ProductCreatedEvent event) {
        createOrderProductUseCase.create(new CreateOrderProductCommand(event.productId(), event.name(), event.price()));
    }
}
