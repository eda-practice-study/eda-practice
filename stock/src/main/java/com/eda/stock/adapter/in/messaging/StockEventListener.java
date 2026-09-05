package com.eda.stock.adapter.in.messaging;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.stock.application.port.in.CreateStockUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final CreateStockUseCase createStockUseCase;

    @KafkaListener(topics = "product-created")
    public void registerProductStock(ProductCreatedEvent event) {
        createStockUseCase.register(event.productId());
    }
}
