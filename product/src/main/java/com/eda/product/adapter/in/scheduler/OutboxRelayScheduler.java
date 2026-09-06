package com.eda.product.adapter.in.scheduler;


import com.eda.product.application.port.in.RelayProductEventUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final RelayProductEventUseCase relayProductEventUseCase;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        relayProductEventUseCase.relay();
    }
}
