package com.eda.order.adapter.out.kafka;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.order.application.port.out.PublishOrderCreatedEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCreatedEventKafkaAdapter implements PublishOrderCreatedEventPort {

    private static final String TOPIC = "order.events";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Override
    public void publish(OrderCreatedEvent event) {
        String key = event.orderId().toString();

        kafkaTemplate.send(
                TOPIC,
                key,
                event
        ).join();
    }
}
