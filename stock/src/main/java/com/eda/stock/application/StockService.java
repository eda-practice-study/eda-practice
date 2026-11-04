package com.eda.stock.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.StockIncreased;
import com.eda.common.message.StockInitialized;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infrastructure.StockRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository repository;
    private final OutboxWriter outboxWriter;
    private final StockRedisRepository stockRedisRepository;

    @Transactional
    public Stock initializeStock(Long productId) {
        Optional<Stock> existingStock =
                repository.findByProductId(productId);

        if (existingStock.isPresent()) {
            return existingStock.get();
        }

        Stock stock = Stock.createFor(productId);
        stock = repository.save(stock);

        outboxWriter.write(
                "stock.events",
                String.valueOf(stock.getProductId()),
                new StockInitialized(
                        stock.getProductId(),
                        stock.getQuantity()
                )
        );

        return stock;
    }

    @Transactional
    public Stock increase(Long productId, int increasedQuantity) {
        Stock stock = repository.findByProductIdForUpdate(productId)
                .orElseThrow(()->
                    new BusinessException(ErrorCode.STOCK_NOT_FOUND)
                );

        // JPA가 관리하는 객체의 값을 변경 -> 변경 감지, 트랜잭션 끝날때 값을 반영, 영속 컨텍스트
        stock.increase(increasedQuantity);

        outboxWriter.write(
                "stock.events",
                String.valueOf(stock.getProductId()),
                new StockIncreased(
                        stock.getProductId(),
                        increasedQuantity,
                        stock.getQuantity()
                )
        );

        return stock;
    }

    public Long increaseAvailable(Long productId, int quantity) {
        try {
            return stockRedisRepository.increase(productId, quantity);
        } catch (DataAccessException e) {
            log.warn(
                    "Redis 재고 증가 실패: productId={}, quantity={}",
                    productId,
                    quantity,
                    e
            );
        }

        return null;
    }
}
