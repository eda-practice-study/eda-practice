package com.eda.stock.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.stock.StockIncreased;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockIncreaseRequestHistory;
import com.eda.stock.domain.StockIncreaseRequestRepository;
import com.eda.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class StockWriteService {

    private final StockRepository stockRepository;
    private final StockIncreaseRequestRepository requestRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public int increase(String key, Long productId, int quantity) {
        // 같은 키의 동시 요청은 재고를 읽기 전에 유일 제약으로 차단한다.
        requestRepository.saveAndFlush(new StockIncreaseRequestHistory(key, productId));

        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        stock.increase(quantity);

        outboxWriter.write("stock.events", productId.toString(),
                new StockIncreased(productId, quantity, stock.getQuantity()));
        return stock.getQuantity();
    }

}
