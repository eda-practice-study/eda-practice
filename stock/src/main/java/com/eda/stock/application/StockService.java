package com.eda.stock.application;

import com.eda.common.message.StockCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public void register(Long productId, String name, BigDecimal price) {

        // 중복 발행 확인
        if(stockRepository.existsByProductId(productId)) {
            return;
        }

        // 재고 등록 (DB)
        Stock stock = Stock.createFor(productId);
        stockRepository.save(stock);

        // (Redis)

        // 이벤트 발행
        outboxWriter.write(
                "stock.events",
                stock.getId().toString(),
                new StockCreated(stock.getId(), stock.getProductId(), stock.getQuantity())
        );
    }
}
