package com.eda.stock.adapter.in.kafka;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.common.event.ProductCreatedEvent;
import com.eda.stock.application.port.in.HandleCreateProductCommand;
import com.eda.stock.application.port.in.HandleOrderCreatedCommand;
import com.eda.stock.application.port.in.HandleOrderCreatedUseCase;
import com.eda.stock.application.port.in.HandleProductCreatedUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final HandleProductCreatedUseCase handleProductCreatedUseCase;
    private final HandleOrderCreatedUseCase handleOrderCreatedUseCase;

    @KafkaListener(
            topics = "product.events",
            groupId = "stock-service-product"
    )
    public void handleProductCreated(
            ProductCreatedEvent event
    ) {
        HandleCreateProductCommand command = new HandleCreateProductCommand(
                event.eventId(),
                event.productId()
        );

        handleProductCreatedUseCase.handle(command);
    }

    @KafkaListener(
            topics = "order.events",
            groupId = "stock-service-order"
    )
    public void handleOrderCreated(
            OrderCreatedEvent event
    ) {
        HandleOrderCreatedCommand command =
                new HandleOrderCreatedCommand(
                        event.eventId(),
                        event.orderId(),
                        event.lines().stream()
                                .map(line->
                                    new HandleOrderCreatedCommand.Line(
                                            line.productId(),
                                            line.quantity()
                                    )
                                )
                                .toList()
                );

        handleOrderCreatedUseCase.handle(command);
    }

}
