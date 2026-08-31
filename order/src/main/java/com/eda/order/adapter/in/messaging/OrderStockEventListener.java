package com.eda.order.adapter.in.messaging;

import com.eda.common.event.StockDeductedEvent;
import com.eda.common.event.StockDeductionFailedEvent;
import com.eda.order.application.port.in.HandleStockResultUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@KafkaListener(
        topics = "stock.events",
        groupId = "order-service.stock"
)
public class OrderStockEventListener {

    private final HandleStockResultUseCase handleStockResultUseCase;

    @KafkaHandler
    public void handle(StockDeductedEvent event) {
        handleStockResultUseCase.handleDeducted(event.orderId());
    }

    @KafkaHandler
    public void handle(StockDeductionFailedEvent event) {
        handleStockResultUseCase.handleDeductionFailed(event.orderId());
    }
}
