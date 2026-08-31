package com.eda.stock.adapter.in.messaging;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.common.event.ProductCreatedEvent;
import com.eda.stock.application.port.in.CreateInitialStockUseCase;
import com.eda.stock.application.port.in.DeductStockCommand;
import com.eda.stock.application.port.in.DeductStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final CreateInitialStockUseCase createInitialStockUseCase;
    private final DeductStockUseCase deductStockUseCase;

    @KafkaListener(
            topics = "product.events",
            groupId = "stock-service.product"
    )
    public void handle(ProductCreatedEvent event) {
        createInitialStockUseCase.create(event.productId());
    }

    @KafkaListener(
            topics = "order.events",
            groupId = "stock-service.order"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        var lines = event.lines().stream()
                .map(line -> new DeductStockCommand.LineCommand(
                        line.productId(),
                        line.quantity()
                ))
                .toList();

        DeductStockCommand command = new DeductStockCommand(
                event.eventId(),
                event.orderId(),
                lines
        );

        deductStockUseCase.deduct(command);
    }
}
