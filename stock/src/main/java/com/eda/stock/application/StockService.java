package com.eda.stock.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.stock.StockCreated;
import com.eda.common.message.stock.StockIncreased;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.api.dto.IncreaseStockResponse;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infrastructure.StockRedisRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockRedisRepository stockRedisRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public void register(Long productId) {

        // 중복 발행 확인
        if (stockRepository.existsByProductId(productId)) {
            return;
        }

        // 재고 등록 (DB)
        Stock stock = Stock.createFor(productId);
        stockRepository.save(stock);

        // (Redis)
        stockRedisRepository.initialize(productId);

        // 이벤트 발행
        outboxWriter.write(
                "stock.events",
                stock.getProductId().toString(),
                new StockCreated(stock.getProductId(), stock.getQuantity())
        );
    }

    @Transactional
    public IncreaseStockResponse increaseStock(Long productId, int quantity) {

        // 재고 조회
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        // 재고 증가
        stock.increase(quantity);

        // 이벤트 발행
        outboxWriter.write(
                "stock.events",
                stock.getProductId().toString(),
                new StockIncreased(stock.getProductId(), stock.getQuantity())
        );

        // TODO: Redis 재고 증가 후 availableQuantity 반환 구현
        Integer availableQuantity = null;

        return IncreaseStockResponse.from(stock.getProductId(), stock.getQuantity(), availableQuantity);
    }
}
