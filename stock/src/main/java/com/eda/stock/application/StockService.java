package com.eda.stock.application;

import com.eda.common.message.StockInitialized;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository repository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public Stock initializeStock(Long productId) {
        Stock initialStock = Stock.createFor(productId);

        // 먼저 DB에 저장 -> inserted 값에 따라 저장됐는지 안됐는지 판단
        int inserted = repository.insertIfAbsent(
                initialStock.getProductId(),
                initialStock.getQuantity()
        );

        Stock stock = repository.findByProductId(productId)
                .orElseThrow();

        // 저장됐으면 Outbox DB에 저장 -> 이후 스케줄러로 이벤트 발행
        if (inserted == 1) {
            outboxWriter.write(
                    "stock.events",
                    String.valueOf(stock.getProductId()),
                    new StockInitialized(
                            stock.getProductId(),
                            stock.getQuantity()
                    )
            );
        }
        return stock;
    }
}
