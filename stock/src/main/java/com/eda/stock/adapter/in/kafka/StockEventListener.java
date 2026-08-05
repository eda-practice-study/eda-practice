package com.eda.stock.adapter.in.kafka;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.stock.application.port.in.CreateStockCommand;
import com.eda.stock.application.port.in.CreateStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final CreateStockUseCase createStockUseCase;

    @KafkaListener(
            topics = "product.events",
            groupId = "stock-service-product"
    )
    public void handleProductCreated(
            ProductCreatedEvent event
    ) {
        CreateStockCommand command = new CreateStockCommand(event.productId());

        createStockUseCase.create(command);
    }
}
