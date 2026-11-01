package com.eda.stock.application;

import com.eda.common.message.Topics;
import com.eda.common.message.stock.StockRegistered;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.infrastructure.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public void register(Long productId) {
        // 멱등성 처리
        if (stockRepository.existsByProductId(productId)) {
            return;
        }

        Stock stock = stockRepository.save(Stock.createFor(productId));
        outboxWriter.write(Topics.STOCK_EVENTS, String.valueOf(productId),
                new StockRegistered(productId, stock.getQuantity()));
    }
}
