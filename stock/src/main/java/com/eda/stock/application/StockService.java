package com.eda.stock.application;

import com.eda.common.message.StockCreated;
import com.eda.common.outbox.OutboxWriter;
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
                stock.getId().toString(),
                new StockCreated(stock.getId(), stock.getProductId(), stock.getQuantity())
        );
    }
}
