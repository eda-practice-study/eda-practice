package com.eda.product.adapter.out.messaging;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.out.PublishProductEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventPublisherAdapter implements PublishProductEventPort {

    private final KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate;

    @Override
    public void publish(ProductCreatedEvent productCreatedEvent) {
        kafkaTemplate.send("product-created", productCreatedEvent)
                .join(); // 발행처리가 너무 빠른시기에 처리될 수 있음
    }
}
