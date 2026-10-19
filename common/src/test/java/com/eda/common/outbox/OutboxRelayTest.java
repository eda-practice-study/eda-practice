package com.eda.common.outbox;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.eda.common.message.Message;
import com.eda.common.message.MessageHeader;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxRelayTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final OutboxProperties properties = new OutboxProperties(true, Duration.ofSeconds(1), 100, Duration.ofSeconds(1));
    private final OutboxRelay relay = new OutboxRelay(repository, kafkaTemplate, properties);

    record SampleCreated(String value) {
    }

    private OutboxMessage message(String key, String payload) {
        return OutboxMessage.create("sample.events", key, Message.of(new SampleCreated(payload)), payload);
    }

    private void givenPending(OutboxMessage... messages) {
        when(repository.findByStatusOrderByIdAsc(eq(OutboxStatus.PENDING), any(Limit.class))).thenReturn(List.of(messages));
    }

    private void kafkaReturns(String payload, CompletableFuture<SendResult<String, String>> result) {
        when(kafkaTemplate.send(argThat((ProducerRecord<String, String> r) -> r != null && payload.equals(r.value()))))
                .thenReturn(result);
    }

    @Test
    @DisplayName("PENDING 메시지를 보내고 PUBLISHED 로 저장한다")
    void publish() {
        // given
        OutboxMessage message = message("A", "a1");
        givenPending(message);
        kafkaReturns("a1", CompletableFuture.completedFuture(null));

        // when
        relay.relay();

        // then
        assertThat(message.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(repository).save(message);
    }

    @Test
    @DisplayName("한 건이 실패하면 이번 회차를 멈추고, 뒤 메시지는 다음 회차에 보낸다")
    void stopRoundOnFailure() {
        // given
        OutboxMessage first = message("A", "a1");
        OutboxMessage second = message("B", "b1");
        givenPending(first, second);
        kafkaReturns("a1", CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        // when
        relay.relay();

        // then
        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(repository, never()).save(any());
        verify(kafkaTemplate, never()).send(argThat((ProducerRecord<String, String> r) -> r != null && "b1".equals(r.value())));
    }

    @Test
    @DisplayName("Kafka 메시지에 키와 messageId · messageType · occurredAt 헤더를 붙인다")
    void record() {
        // given
        OutboxMessage message = message("A", "a1");
        givenPending(message);
        kafkaReturns("a1", CompletableFuture.completedFuture(null));

        // when
        relay.relay();

        // then
        verify(kafkaTemplate).send(argThat((ProducerRecord<String, String> r) ->
                r != null
                        && "A".equals(r.key())
                        && message.getMessageId().toString().equals(header(r, MessageHeader.MESSAGE_ID))
                        && "SampleCreated".equals(header(r, MessageHeader.MESSAGE_TYPE))
                        && message.getOccurredAt().toString().equals(header(r, MessageHeader.OCCURRED_AT))));
    }

    private String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value());
    }
}
