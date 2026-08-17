package com.eda.product.adapter.in.scheduler;

import com.eda.product.application.port.in.PublishPendingEventsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class OutboxPollingScheduler {

    private final PublishPendingEventsUseCase publishPendingEventsUseCase;

    @Scheduled(fixedDelayString = "1000")
    void poll() {
        publishPendingEventsUseCase.publishPending();
    }
}
