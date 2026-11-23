package com.eda.stock.application;

import com.eda.stock.domain.Stock;
import com.eda.stock.infrastructure.RedisStock;
import com.eda.stock.infrastructure.StockRedisRepository;
import com.eda.stock.infrastructure.StockRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "stock.reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StockReconciler {

    private final StockRepository stockRepository;
    private final StockRedisRepository stockRedisRepository;

    @Value("${stock.reconcile.grace:10s}")
    private Duration grace;

    @Scheduled(fixedDelayString = "${stock.reconcile.interval:60s}")
    @SchedulerLock(name = "stock-reconcile", lockAtMostFor = "PT5M")
    public void reconcile() {
        LocalDateTime settledBefore = LocalDateTime.now().minus(grace);
        for (Stock stock : stockRepository.findByUpdatedAtBefore(settledBefore)) {
            try {
                reconcile(stock);
            } catch (RuntimeException e) {
                log.warn("재고 대사 실패 productId={}", stock.getProductId(), e);
            }
        }
    }

    private void reconcile(Stock stock) {
        Optional<RedisStock> redisStock = stockRedisRepository.find(stock.getProductId());
        if (redisStock.isPresent() && redisStock.get().total() == stock.getQuantity()) {
            return;
        }

        if (stockRedisRepository.resync(stock.getProductId(), stock.getQuantity())) {
            log.warn("재고 재동기화 productId={}, db={}, redis={}",
                    stock.getProductId(), stock.getQuantity(), redisStock.orElse(null));
        } else {
            log.warn("예약 진행 중이라 재동기화 보류 productId={}, db={}, redis={}",
                    stock.getProductId(), stock.getQuantity(), redisStock.orElse(null));
        }
    }
}
