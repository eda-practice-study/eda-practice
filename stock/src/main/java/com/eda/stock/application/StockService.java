package com.eda.stock.application;

import com.eda.common.message.Topics;
import com.eda.common.message.stock.StockRegistered;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.infrastructure.StockRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
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
}
