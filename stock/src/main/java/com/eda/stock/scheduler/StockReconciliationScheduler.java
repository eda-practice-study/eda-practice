package com.eda.stock.scheduler;

import com.eda.stock.service.StockReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "stock.reconciliation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StockReconciliationScheduler {

    private final StockReconciliationService stockReconciliationService;

    @Scheduled(fixedDelayString = "${stock.reconciliation.interval:60s}")
    public void reconcile() {
        stockReconciliationService.reconcile();
    }
}
