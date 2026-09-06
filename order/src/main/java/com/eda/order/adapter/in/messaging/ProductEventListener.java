package com.eda.order.adapter.in.messaging;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.order.application.port.in.SynchronizeProductCatalogUseCase;
import com.eda.order.application.port.in.command.SynchronizeProductCatalogCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final SynchronizeProductCatalogUseCase useCase;

    @KafkaListener(
            topics = "product-created",
            groupId = "order-product-catalog"
    )
    public void handle(ProductCreatedEvent event) {
        useCase.synchronize(
                new SynchronizeProductCatalogCommand(
                        event.productId(),
                        event.productName(),
                        event.unitPrice()
                )
        );
    }
}