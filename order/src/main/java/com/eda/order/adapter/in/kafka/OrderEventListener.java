package com.eda.order.adapter.in.kafka;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.common.event.StockDeductionResultEvent;
import com.eda.order.application.port.in.HandleProductCreatedCommand;
import com.eda.order.application.port.in.HandleProductCreatedUseCase;
import com.eda.order.application.port.in.HandleStockDeductionResultCommand;
import com.eda.order.application.port.in.HandleStockDeductionResultUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final HandleProductCreatedUseCase handleProductCreatedUseCase;

    private final HandleStockDeductionResultUseCase handleStockDeductionResultUseCase;

    @KafkaListener(
            topics = "product.events",
            groupId = "order-service-product"
    )
    public void handleProductCreated(ProductCreatedEvent event) {
        HandleProductCreatedCommand command =
                new HandleProductCreatedCommand(
                        event.eventId(),
                        event.productId(),
                        event.name(),
                        event.price()
                );
        handleProductCreatedUseCase.handle(command);
    }

    @KafkaListener(
            topics = "stock.events",
            groupId = "order-service-stock"
    )
    public void handleStockDeductionResult(
            StockDeductionResultEvent event
    ) {
        HandleStockDeductionResultCommand command =
                new HandleStockDeductionResultCommand(
                        event.eventId(),
                        event.orderId(),
                        event.result()
                );
        handleStockDeductionResultUseCase.handle(command);
    }
}
