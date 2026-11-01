package com.eda.stock.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.stock.StockIncreased;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.redis.AvailableStockStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockIncreaseService {

    private static final String TOPIC = "stock.events";

    private final StockRepository stockRepository;
    private final OutboxWriter outboxWriter;
    private final AvailableStockStore availableStockStore;

    public record Result(Long productId, int quantity, Long availableQuantity) {
    }

    // Redis 반영은 행 락을 잡은 채 커밋 전에 한다. 나중에 붙일 Redis 복구와 같은 락으로 줄을 세우기 위해서다.
    @Transactional
    public Result increase(Long productId, int quantity) {
        Stock stock = stockRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        stock.increase(quantity);
        stockRepository.flush();

        Long available = increaseAvailable(productId, quantity);
        outboxWriter.write(TOPIC, String.valueOf(productId),
                new StockIncreased(productId, quantity, stock.getQuantity()));
        return new Result(productId, stock.getQuantity(), available);
    }

    // Redis 가 실패해도 DB 는 반영하고 null 로 응답한다
    private Long increaseAvailable(Long productId, int quantity) {
        Long available;
        try {
            available = availableStockStore.increaseIfPresent(productId, quantity);
        } catch (RuntimeException e) {
            log.warn("가용 재고 반영 실패 productId={}", productId, e);
            return null;
        }

        if (available != null) {
            compensateOnRollback(productId, quantity);
        }
        return available;
    }

    private void compensateOnRollback(Long productId, int quantity) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK) {
                    return;
                }
                try {
                    availableStockStore.increaseIfPresent(productId, -quantity);
                } catch (RuntimeException e) {
                    log.error("가용 재고 보상 실패 productId={} quantity={}", productId, quantity, e);
                }
            }
        });
    }
}
