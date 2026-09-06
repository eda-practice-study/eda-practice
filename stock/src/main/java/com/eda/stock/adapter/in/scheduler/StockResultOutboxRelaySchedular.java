package com.eda.stock.adapter.in.scheduler;

import com.eda.stock.application.port.in.RelayStockResultEventUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockResultOutboxRelaySchedular {

    private final RelayStockResultEventUseCase relayStockResultEventUseCase;

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        relayStockResultEventUseCase.relay();
    }
}
