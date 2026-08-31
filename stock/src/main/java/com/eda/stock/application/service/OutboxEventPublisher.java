package com.eda.stock.application.service;

import com.eda.common.event.StockDeductedEvent;
import com.eda.common.event.StockDeductionFailedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.out.OutboxEventPort;
import com.eda.stock.application.port.out.PublishStockEventPort;
import com.eda.stock.domain.outbox.OutboxEvent;
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
    private final PublishStockEventPort eventPublisher;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        for(OutboxEvent outboxEvent : outboxEventPort.findPending()) {
            publish(outboxEvent);
        }
    }

    private void publish(OutboxEvent outboxEvent) {
        switch (outboxEvent.getEventType()) {
            case "STOCK_DEDUCTED" -> {
                StockDeductedEvent event = deserialize(
                        outboxEvent.getPayload(),
                        StockDeductedEvent.class
                );
                eventPublisher.publish(event);
            }

            case "STOCK_DEDUCTION_FAILED" -> {
                StockDeductionFailedEvent event = deserialize(
                        outboxEvent.getPayload(),
                        StockDeductionFailedEvent.class
                );
                eventPublisher.publish(event);
            }

            default -> throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        outboxEvent.markPublished(LocalDateTime.now());
        outboxEventPort.save(outboxEvent);
    }

    private <T> T deserialize(String payload, Class<T> eventType) {
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
