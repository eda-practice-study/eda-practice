package com.eda.stock.adapter.in.messaging;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.stock.application.port.in.CreateInitialStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final CreateInitialStockUseCase createInitialStockUseCase;

    @KafkaListener(
            topics = "product.events",
            groupId = "stock-service.product"
    )
    public void handle(ProductCreatedEvent event) {
        createInitialStockUseCase.create(event.productId());
    }
}
