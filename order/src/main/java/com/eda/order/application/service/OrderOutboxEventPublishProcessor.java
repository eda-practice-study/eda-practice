package com.eda.order.application.service;

import com.eda.common.event.OrderCreated;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.out.LoadOrderOutboxEventPort;
import com.eda.order.application.port.out.PublishOrderEventPort;
import com.eda.order.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OrderOutboxEventPublishProcessor {

    private final LoadOrderOutboxEventPort loadOutboxEventPort;
    private final PublishOrderEventPort publishOrderEventPort;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(Long outboxEventId) {
        OutboxEvent outboxEvent = loadOutboxEventPort.findByIdForUpdate(outboxEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
        if (outboxEvent.isPublished()) {
            return;
        }

        OrderCreated event = objectMapper.readValue(outboxEvent.getPayload(), OrderCreated.class);
        publishOrderEventPort.publish(event);
        outboxEvent.markPublished();
    }
}
