package com.eda.stock.application.service;

import com.eda.stock.application.port.in.PublishPendingStockEventsUseCase;
import com.eda.stock.application.port.out.LoadStockOutboxEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockOutboxEventPublishService implements PublishPendingStockEventsUseCase {

    private final LoadStockOutboxEventPort loadOutboxEventPort;
    private final StockOutboxEventPublishProcessor publishProcessor;

    @Override
    public void publishPending() {
        for (Long outboxEventId : loadOutboxEventPort.findUnpublishedIds()) {
            try {
                publishProcessor.publishOne(outboxEventId);
            } catch (Exception e) {
                log.error("재고 Outbox 이벤트 발행 실패 outboxEventId={}", outboxEventId, e);
            }
        }
    }
}
