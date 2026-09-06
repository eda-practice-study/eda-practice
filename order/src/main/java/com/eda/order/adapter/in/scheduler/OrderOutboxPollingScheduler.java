package com.eda.order.adapter.in.scheduler;

import com.eda.order.application.port.in.PublishPendingOrderEventsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OrderOutboxPollingScheduler {

    private final PublishPendingOrderEventsUseCase publishPendingOrderEventsUseCase;

    @Scheduled(fixedDelayString = "1000")
    void poll() {
        publishPendingOrderEventsUseCase.publishPending();
    }
}
