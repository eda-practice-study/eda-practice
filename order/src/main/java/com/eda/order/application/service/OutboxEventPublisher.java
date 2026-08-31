package com.eda.order.application.service;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.out.OutboxEventPort;
import com.eda.order.application.port.out.PublishOrderCreatedEventPort;
import com.eda.order.domain.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventPort outboxEventPort;
    private final PublishOrderCreatedEventPort eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        for (OutboxEvent outboxEvent : outboxEventPort.findPending()) {
            publish(outboxEvent);
        }
    }

    private void publish(OutboxEvent outboxEvent) {
        OrderCreatedEvent event = deserialize(outboxEvent.getPayload());

        eventPublisher.publish(event);

        outboxEvent.markPublished(LocalDateTime.now());
        outboxEventPort.save(outboxEvent);
    }

    private OrderCreatedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(
                    payload,
                    OrderCreatedEvent.class
            );
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
