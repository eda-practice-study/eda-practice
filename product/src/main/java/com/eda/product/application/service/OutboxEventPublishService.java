package com.eda.product.application.service;

import com.eda.product.application.port.in.PublishPendingEventsUseCase;
import com.eda.product.application.port.out.LoadOutboxEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventPublishService implements PublishPendingEventsUseCase {

    private final LoadOutboxEventPort loadOutboxEventPort;
    private final OutboxEventPublishProcessor outboxEventPublishProcessor;

    @Override
    public void publishPending() {
        for (Long outboxEventId : loadOutboxEventPort.findUnpublishedIds()) {
            try {
                outboxEventPublishProcessor.publishOne(outboxEventId);
            } catch (Exception e) {
                log.error("Outbox 이벤트 발행 실패 outboxEventId={}", outboxEventId, e);
            }
        }
    }
}
