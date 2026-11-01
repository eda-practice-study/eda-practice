package com.eda.stock.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.Topics;
import com.eda.common.message.stock.StockIncreased;
import com.eda.common.message.stock.StockRegistered;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.infrastructure.StockRedisRepository;
import com.eda.stock.infrastructure.StockRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockRedisRepository stockRedisRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public Stock register(Long productId) {
        Optional<Stock> registered = stockRepository.findByProductId(productId);
        if (registered.isPresent()) {
            return registered.get();
        }

        Stock stock = stockRepository.save(Stock.createFor(productId));
        outboxWriter.write(Topics.STOCK_EVENTS, String.valueOf(productId),
                new StockRegistered(productId, stock.getQuantity()));

        return stock;
    }

    @Transactional
    public Stock increase(Long productId, int quantity) {
        Stock stock = stockRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        stock.increase(quantity);
        outboxWriter.write(Topics.STOCK_EVENTS, String.valueOf(productId),
                new StockIncreased(productId, quantity, stock.getQuantity()));

        return stock;
    }

    public Long increaseAvailable(Long productId, int quantity) {
        try {
            return stockRedisRepository.increaseAvailable(productId, quantity);
        } catch (RuntimeException e) {
            log.warn("Redis 재고 증가 실패 productId={}, quantity={}", productId, quantity, e);
            return null;
        }
    }
}
