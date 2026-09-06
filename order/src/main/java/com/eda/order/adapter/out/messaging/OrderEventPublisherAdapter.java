package com.eda.order.adapter.out.messaging;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.order.application.port.out.PublishOrderEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventPublisherAdapter
        implements PublishOrderEventPort {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Override
    public void publish(OrderCreatedEvent event) {
        kafkaTemplate.send(
                "order-created",
                event.orderId().toString(),
                event
        ).join();
    }
}
