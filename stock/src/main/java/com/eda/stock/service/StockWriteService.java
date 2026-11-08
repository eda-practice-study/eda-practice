package com.eda.stock.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.stock.StockIncreased;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.RequestHistory;
import com.eda.stock.domain.RequestHistoryRepository;
import com.eda.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class StockWriteService {

    private static final String STOCK_INCREASE = "STOCK_INCREASE";

    private final StockRepository stockRepository;
    private final RequestHistoryRepository requestRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public Stock increase(String key, Long productId, int quantity) {
        requestRepository.save(new RequestHistory(STOCK_INCREASE, key));

        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        stock.increase(quantity);

        outboxWriter.write("stock.events", productId.toString(),
                new StockIncreased(productId, quantity, stock.getQuantity()));
        return stock;
    }

}
