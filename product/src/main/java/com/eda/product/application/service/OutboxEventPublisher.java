package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.product.application.port.out.OutboxEventPort;
import com.eda.product.application.port.out.PublishProductCreatedEventPort;
import com.eda.product.domain.outbox.OutboxEvent;
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
    private final PublishProductCreatedEventPort eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        for(OutboxEvent outboxEvent : outboxEventPort.findPending()) {
            publish(outboxEvent);
        }
    }

    private void publish(OutboxEvent outboxEvent) {
        ProductCreatedEvent event = deserialize(outboxEvent.getPayload());

        eventPublisher.publish(event);

        outboxEvent.markPublished(LocalDateTime.now());
        outboxEventPort.save(outboxEvent);
    }

    private ProductCreatedEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, ProductCreatedEvent.class);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
