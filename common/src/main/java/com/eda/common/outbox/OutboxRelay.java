package com.eda.common.outbox;

import com.eda.common.message.MessageHeader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${outbox.interval:1s}")
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT1M")
    public void relay() {
        List<OutboxMessage> pending =
                outboxRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, Limit.of(properties.batchSize()));

        for (OutboxMessage message : pending) {
            if (!send(message)) {
                return;
            }
            message.markPublished(LocalDateTime.now());
            outboxRepository.save(message);
        }
    }

    private boolean send(OutboxMessage message) {
        try {
            kafkaTemplate.send(toRecord(message))
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            log.warn("outbox 발행 실패 id={}", message.getId(), e);
            return false;
        }
    }

    private ProducerRecord<String, String> toRecord(OutboxMessage message) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(message.getTopic(), message.getMessageKey(), message.getPayload());

        record.headers().add(MessageHeader.MESSAGE_ID, bytes(message.getMessageId().toString()));
        record.headers().add(MessageHeader.MESSAGE_TYPE, bytes(message.getMessageType()));
        record.headers().add(MessageHeader.OCCURRED_AT, bytes(message.getOccurredAt().toString()));
        return record;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
