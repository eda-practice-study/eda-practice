package com.eda.order.adapter.in.messaging;

import com.eda.common.event.StockDeductedEvent;
import com.eda.common.event.StockDeductionFailedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.HandleStockResultUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderStockEventListener {

    private final HandleStockResultUseCase handleStockResultUseCase;

    @KafkaListener(
            topics = "stock.events",
            groupId = "order-service.stock"
    )
    public void handle(Object event) {
        if (event instanceof StockDeductedEvent deductedEvent) {
            handleStockResultUseCase.handleDeducted(
                    deductedEvent.orderId()
            );
            return;
        }

        if (event instanceof StockDeductionFailedEvent failedEvent) {
            handleStockResultUseCase.handleDeductionFailed(
                    failedEvent.orderId()
            );
            return;
        }

        throw new BusinessException(ErrorCode.INTERNAL_ERROR);
    }
}
