package com.eda.order.application.service;

import com.eda.order.application.port.in.PublishPendingOrderEventsUseCase;
import com.eda.order.application.port.out.LoadOrderOutboxEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderOutboxEventPublishService implements PublishPendingOrderEventsUseCase {

    private final LoadOrderOutboxEventPort loadOutboxEventPort;
    private final OrderOutboxEventPublishProcessor publishProcessor;

    @Override
    public void publishPending() {
        for (Long outboxEventId : loadOutboxEventPort.findUnpublishedIds()) {
            try {
                publishProcessor.publishOne(outboxEventId);
            } catch (Exception e) {
                log.error("주문 Outbox 이벤트 발행 실패 outboxEventId={}", outboxEventId, e);
            }
        }
    }
}
