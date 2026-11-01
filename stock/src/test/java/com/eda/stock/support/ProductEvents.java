package com.eda.stock.support;

import com.eda.common.message.MessageHeader;
import com.eda.common.message.product.ProductCreated;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class ProductEvents {

    private ProductEvents() {
    }

    public static ProducerRecord<String, String> productCreated(UUID messageId, ProductCreated payload) {
        ProducerRecord<String, String> record = new ProducerRecord<>("product.events",
                String.valueOf(payload.productId()),
                "{\"productId\":%d,\"name\":\"%s\",\"price\":%d}".formatted(
                        payload.productId(), payload.name(), payload.price()));
        record.headers().add(MessageHeader.MESSAGE_ID, bytes(messageId.toString()));
        record.headers().add(MessageHeader.MESSAGE_TYPE, bytes("ProductCreated"));
        record.headers().add(MessageHeader.OCCURRED_AT, bytes(Instant.now().toString()));
        return record;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
