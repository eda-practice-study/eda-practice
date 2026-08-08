package com.eda.stock.adapter.in.messaging;

import com.eda.common.event.ProductCreated;
import com.eda.common.event.Topics;
import com.eda.stock.application.port.in.CreateStockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class StockEventKafkaListener {

    private final CreateStockUseCase createStockUseCase;

    @KafkaListener(
            topics = Topics.PRODUCT_EVENTS,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    void onProductCreated(ProductCreated event,
                          @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                          @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("ProductCreated 수신 productId={}, partition={}, offset={}", event.productId(), partition, offset);
        createStockUseCase.createFor(event.productId());
    }
}
