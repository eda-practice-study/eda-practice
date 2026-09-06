package com.eda.order.adapter.in.scheduler;


import com.eda.order.application.port.in.RelayOrderCreatedEventUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCreatedOutboxRelayScheduler {

    private final RelayOrderCreatedEventUseCase relayOrderCreatedEventUseCase;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        relayOrderCreatedEventUseCase.relay();
    }
}
