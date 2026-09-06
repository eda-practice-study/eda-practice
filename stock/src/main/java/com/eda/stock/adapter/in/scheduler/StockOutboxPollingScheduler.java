package com.eda.stock.adapter.in.scheduler;

import com.eda.stock.application.port.in.PublishPendingStockEventsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class StockOutboxPollingScheduler {

    private final PublishPendingStockEventsUseCase publishPendingStockEventsUseCase;

    @Scheduled(fixedDelayString = "1000")
    void poll() {
        publishPendingStockEventsUseCase.publishPending();
    }
}
