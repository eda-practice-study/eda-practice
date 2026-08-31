package com.eda.order.adapter.out.messaging;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.order.application.port.out.PublishOrderCreatedEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventPublisherAdapter implements PublishOrderCreatedEventPort {

    private static final String ORDER_EVENTS_TOPIC = "order.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publish(OrderCreatedEvent event) {
        kafkaTemplate.send(
                ORDER_EVENTS_TOPIC,
                event.orderId().toString(),
                event
        ).join();
    }
}
