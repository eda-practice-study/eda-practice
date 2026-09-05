package com.eda.product.adapter.in.scheduler;

import com.eda.product.application.port.in.PublishPendingOutboxEventUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxPollingScheduler {

    private final PublishPendingOutboxEventUseCase publishPendingOutboxEventUseCase;

    @Scheduled(fixedDelay = 1000)
    public void publishPendingEvents() {
        publishPendingOutboxEventUseCase.publishPendingEvents();
    }

}
