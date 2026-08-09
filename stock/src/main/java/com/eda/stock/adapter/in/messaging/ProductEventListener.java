package com.eda.stock.adapter.in.messaging;

import com.eda.common.event.EventTopics;
import com.eda.common.event.ProductCreatedEvent;
import com.eda.stock.application.port.in.CreateStockUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventListener {

    private final CreateStockUseCase createStockUseCase;

    @KafkaListener(topics = EventTopics.PRODUCT_EVENTS, groupId = "stock-product-events")
    public void onProductCreated(ProductCreatedEvent event) {
        log.info("상품 생성 이벤트 수신. eventId={}, productId={}", event.eventId(), event.productId());
        createStockUseCase.create(event.productId());
    }
}
