package com.eda.product.application.service;

import com.eda.common.event.ProductCreated;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.product.application.port.out.LoadOutboxEventPort;
import com.eda.product.application.port.out.PublishEventPort;
import com.eda.product.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxEventPublishProcessor {

    private final LoadOutboxEventPort loadOutboxEventPort;
    private final PublishEventPort publishEventPort;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(Long outboxEventId) {
        OutboxEvent outboxEvent = loadOutboxEventPort.findByIdForUpdate(outboxEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
        if (outboxEvent.isPublished()) {
            return;
        }
        ProductCreated event = objectMapper.readValue(outboxEvent.getPayload(), ProductCreated.class);
        publishEventPort.publish(event);
        outboxEvent.markPublished();
    }
}
