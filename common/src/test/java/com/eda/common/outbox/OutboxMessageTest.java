package com.eda.common.outbox;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.message.Message;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxMessageTest {

    record SampleCreated(Long id) {
    }

    private final Message<SampleCreated> sample = Message.of(new SampleCreated(1L));

    private OutboxMessage pending() {
        return OutboxMessage.create("sample.events", "1", sample, "{\"id\":1}");
    }

    @Test
    @DisplayName("생성하면 PENDING 이고 메시지의 ID · 타입 · 발생 시각을 그대로 담는다")
    void create() {
        OutboxMessage message = pending();

        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(message.getMessageId()).isEqualTo(sample.messageId());
        assertThat(message.getMessageType()).isEqualTo("SampleCreated");
        assertThat(message.getOccurredAt()).isEqualTo(sample.occurredAt());
    }

    @Test
    @DisplayName("필수 값이 비어 있으면 생성에 실패한다")
    void failWithBlank() {
        assertThatThrownBy(() -> OutboxMessage.create(" ", "1", sample, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OutboxMessage.create("sample.events", "1", null, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("발행되면 PUBLISHED 가 되고 발행 시각을 남긴다")
    void markPublished() {
        // given
        OutboxMessage message = pending();
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 10, 0);

        // when
        message.markPublished(now);

        // then
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(message.getPublishedAt()).isEqualTo(now);
    }
}
