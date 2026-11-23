package com.eda.stock.support;

import com.eda.common.message.MessageHeader;
import com.eda.common.message.order.OrderCreated;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class OrderEvents {

    private OrderEvents() {
    }

    public static ProducerRecord<String, String> orderCreated(UUID messageId, OrderCreated payload) {
        String items = payload.items().stream()
                .map(item -> "{\"productId\":%d,\"quantity\":%d}".formatted(item.productId(), item.quantity()))
                .collect(Collectors.joining(","));
        return record(messageId, "OrderCreated", String.valueOf(payload.orderId()),
                "{\"orderId\":%d,\"orderKey\":\"%s\",\"items\":[%s]}".formatted(
                        payload.orderId(), payload.orderKey(), items));
    }

    public static ProducerRecord<String, String> record(UUID messageId, String type, String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>("order.events", key, value);
        record.headers().add(MessageHeader.MESSAGE_ID, bytes(messageId.toString()));
        record.headers().add(MessageHeader.MESSAGE_TYPE, bytes(type));
        record.headers().add(MessageHeader.OCCURRED_AT, bytes(Instant.now().toString()));
        return record;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
