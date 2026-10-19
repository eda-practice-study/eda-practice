package com.eda.common.message;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class MessageTest {

    record SampleCreated(Long id, String name) {
    }

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("payload 로 만들면 messageId · 발생 시각을 발급하고 타입은 payload 클래스 이름이다")
    void of() {
        Message<SampleCreated> message = Message.of(new SampleCreated(1L, "키보드"));

        assertThat(message.messageId()).isNotNull();
        assertThat(message.messageType()).isEqualTo("SampleCreated");
        assertThat(message.occurredAt()).isNotNull();
        assertThat(message.payload()).isEqualTo(new SampleCreated(1L, "키보드"));
    }

    @Test
    @DisplayName("payload 가 없으면 만들 수 없다")
    void failWithoutPayload() {
        assertThatThrownBy(() -> Message.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Kafka 레코드의 헤더와 value 로 메시지를 읽는다")
    void from() {
        // given
        UUID messageId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-10-20T01:00:00Z");
        ConsumerRecord<String, String> record = new ConsumerRecord<>("sample.events", 0, 0L, "1", "{\"id\":1,\"name\":\"키보드\"}");
        record.headers().add(MessageHeader.MESSAGE_ID, bytes(messageId.toString()));
        record.headers().add(MessageHeader.MESSAGE_TYPE, bytes("SampleCreated"));
        record.headers().add(MessageHeader.OCCURRED_AT, bytes(occurredAt.toString()));

        // when
        Message<SampleCreated> message = Message.from(record, SampleCreated.class, jsonMapper);

        // then
        assertThat(message).isEqualTo(new Message<>(messageId, "SampleCreated", occurredAt, new SampleCreated(1L, "키보드")));
    }

    @Test
    @DisplayName("규격 헤더가 없으면 읽을 수 없다")
    void failWithoutHeader() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("sample.events", 0, 0L, "1", "{}");

        assertThatThrownBy(() -> Message.from(record, SampleCreated.class, jsonMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(MessageHeader.MESSAGE_ID);
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
