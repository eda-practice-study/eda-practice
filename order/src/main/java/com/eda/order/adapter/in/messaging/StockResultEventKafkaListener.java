package com.eda.order.adapter.in.messaging;

import com.eda.common.event.StockDeductionResult;
import com.eda.common.event.Topics;
import com.eda.order.application.port.in.HandleStockResultUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class StockResultEventKafkaListener {

    private final HandleStockResultUseCase handleStockResultUseCase;

    @KafkaListener(
            topics = Topics.STOCK_EVENTS,
            groupId = "${spring.kafka.consumer.stock-group-id}"
    )
    void onStockResult(StockDeductionResult event,
                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                       @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("재고 차감 결과 수신 orderId={}, partition={}, offset={}",
                event.orderId(), partition, offset);
        handleStockResultUseCase.handle(event);
    }
}
