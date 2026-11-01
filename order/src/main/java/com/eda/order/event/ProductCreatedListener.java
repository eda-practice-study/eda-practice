package com.eda.order.event;

import com.eda.common.message.Message;
import com.eda.common.message.product.ProductCreated;
import com.eda.order.application.ProductSnapshotService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ProductCreatedListener {

    private final ProductSnapshotService productSnapshotService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "product.events", groupId = "order-service")
    public void on(ConsumerRecord<String, String> record) {
        Message<ProductCreated> message = Message.from(record, ProductCreated.class, jsonMapper);
        productSnapshotService.registerIfAbsent(message.payload());
    }
}
