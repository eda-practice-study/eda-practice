package com.eda.stock.adapter.in.messaging;

import com.eda.common.event.OrderCreated;
import com.eda.common.event.Topics;
import com.eda.stock.application.port.in.DeductStockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class StockOrderEventKafkaListener {

    private final DeductStockUseCase deductStockUseCase;

    @KafkaListener(
            topics = Topics.ORDER_EVENTS,
            groupId = "${spring.kafka.consumer.order-group-id}"
    )
    void onOrderCreated(OrderCreated event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("OrderCreated 수신 orderId={}, partition={}, offset={}",
                event.orderId(), partition, offset);
        deductStockUseCase.deduct(event);
    }
}
