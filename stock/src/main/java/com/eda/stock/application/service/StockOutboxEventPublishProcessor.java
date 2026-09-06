package com.eda.stock.application.service;

import com.eda.common.event.EventTypes;
import com.eda.common.event.StockDeducted;
import com.eda.common.event.StockDeductionFailed;
import com.eda.common.event.StockDeductionResult;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.out.LoadStockOutboxEventPort;
import com.eda.stock.application.port.out.PublishStockEventPort;
import com.eda.stock.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class StockOutboxEventPublishProcessor {

    private final LoadStockOutboxEventPort loadOutboxEventPort;
    private final PublishStockEventPort publishStockEventPort;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(Long outboxEventId) {
        OutboxEvent outboxEvent = loadOutboxEventPort.findByIdForUpdate(outboxEventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.OUTBOX_EVENT_NOT_FOUND));
        if (outboxEvent.isPublished()) {
            return;
        }

        StockDeductionResult event = readEvent(outboxEvent);
        publishStockEventPort.publish(event);
        outboxEvent.markPublished();
    }

    private StockDeductionResult readEvent(OutboxEvent outboxEvent) {
        if (outboxEvent.getEventType() == EventTypes.StockDeducted) {
            return objectMapper.readValue(outboxEvent.getPayload(), StockDeducted.class);
        }
        if (outboxEvent.getEventType() == EventTypes.StockDeductionFailed) {
            return objectMapper.readValue(outboxEvent.getPayload(), StockDeductionFailed.class);
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}
