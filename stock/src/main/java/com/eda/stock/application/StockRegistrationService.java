package com.eda.stock.application;

import com.eda.common.message.stock.StockRegistered;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.redis.AvailableStockStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockRegistrationService {

    private static final String TOPIC = "stock.events";

    private final StockRepository stockRepository;
    private final OutboxWriter outboxWriter;
    private final AvailableStockStore availableStockStore;

    // 같은 상품 생성 이벤트가 다시 와도 재고 행과 이벤트는 한 번만 만든다
    @Transactional
    public void registerIfAbsent(Long productId) {
        if (stockRepository.existsByProductId(productId)) {
            return;
        }

        Stock stock = stockRepository.save(Stock.createFor(productId));
        outboxWriter.write(TOPIC, String.valueOf(productId), new StockRegistered(productId, stock.getQuantity()));
    }

    // 재고 증가와 같은 행 락 안에서 DB 수량을 읽어 넣는다. 0 을 고정으로 넣으면 그사이 늘어난 재고를 잃는다.
    @Transactional
    public void initializeAvailable(Long productId) {
        stockRepository.findByProductIdForUpdate(productId)
                .ifPresent(stock -> availableStockStore.initialize(productId, stock.getQuantity()));
    }
}
