package com.eda.order.event;

import com.eda.common.event.ProductCreated;
import com.eda.common.message.Message;
import com.eda.common.message.MessageHeader;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.domain.ProductSnapshotRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ProductCreatedListener {

    private final ProductSnapshotRepository snapshots;
    private final OutboxWriter outbox;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "product.events", groupId = "order-product-snapshot")
    @Transactional
    public void register(ConsumerRecord<String, String> record) {
        var type = record.headers().lastHeader(MessageHeader.MESSAGE_TYPE);
        if (type == null || !"ProductCreated".equals(new String(type.value(), StandardCharsets.UTF_8))) {
            return;
        }

        ProductCreated product = Message.from(record, ProductCreated.class, jsonMapper).payload();
        if (snapshots.register(product.productId(), product.name(), BigDecimal.valueOf(product.price())) == 1) {
            outbox.write("order.events", product.productId().toString(),
                    new ProductSnapshotRegistered(product.productId(), product.name(), product.price()));
        }
    }
}
