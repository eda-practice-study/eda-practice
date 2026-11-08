package com.eda.order.event;

import com.eda.common.message.Message;
import com.eda.common.message.product.ProductCreated;
import com.eda.order.application.ProductSnapshotService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final ProductSnapshotService productSnapshotService;

    @KafkaListener(topics = "product.events", groupId = "order-service")
    public void handleProductCreated(ConsumerRecord<String, String> record) {

        if (!Message.isType(record, ProductCreated.class)) {
            return;
        }

        ProductCreated event = Message.from(record, ProductCreated.class).payload();
        productSnapshotService.create(event.productId(), event.name(), event.price());
    }
}
