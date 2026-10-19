package com.eda.common.message;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import tools.jackson.databind.json.JsonMapper;

public record Message<T>(
        UUID messageId,
        String messageType,
        Instant occurredAt,
        T payload
) {

    public static <T> Message<T> of(T payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload 는 필수입니다");
        }

        return new Message<>(UUID.randomUUID(), payload.getClass().getSimpleName(), Instant.now(), payload);
    }

    public static <T> Message<T> from(ConsumerRecord<String, String> record, Class<T> payloadType, JsonMapper jsonMapper) {
        return new Message<>(
                UUID.fromString(header(record, MessageHeader.MESSAGE_ID)),
                header(record, MessageHeader.MESSAGE_TYPE),
                Instant.parse(header(record, MessageHeader.OCCURRED_AT)),
                jsonMapper.readValue(record.value(), payloadType));
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            throw new IllegalArgumentException(name + " 헤더가 없습니다");
        }

        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
