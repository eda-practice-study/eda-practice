package com.eda.product.adapter.out.kafka;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.out.PublishProductEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventKafkaAdapter implements PublishProductEventPort {

    private static final String TOPIC = "product.events";

    private final KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate;

    @Override
    public void publish(ProductCreatedEvent event) {
        String key = event.productId().toString();

        kafkaTemplate.send(
                TOPIC,
                key,
                event
        );
    }
}
