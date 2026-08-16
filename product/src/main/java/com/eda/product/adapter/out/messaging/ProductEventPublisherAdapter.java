package com.eda.product.adapter.out.messaging;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.out.PublishProductCreatedEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventPublisherAdapter implements PublishProductCreatedEventPort {

    private static final String PRODUCT_EVENTS_TOPIC = "product.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publish(ProductCreatedEvent event) {
        kafkaTemplate.send(
                PRODUCT_EVENTS_TOPIC,
                event.productId().toString(),
                event
        ).join();
    }
}
