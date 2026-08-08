package com.eda.stock.adapter.in.kafka;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.stock.application.port.in.CreateStockCommand;
import com.eda.stock.application.port.in.CreateStockUseCase;
import com.eda.stock.application.port.in.HandleCreateProductCommand;
import com.eda.stock.application.port.in.HandleProductCreatedUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final HandleProductCreatedUseCase handleProductCreatedUseCase;

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
}
