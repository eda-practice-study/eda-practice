package com.eda.order.adapter.in.scheduler;

import com.eda.order.application.port.in.PublishPendingOrderOutboxEventUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderOutboxPollingScheduler {

    private final PublishPendingOrderOutboxEventUseCase useCase;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        useCase.publishPendingEvents();
    }
}