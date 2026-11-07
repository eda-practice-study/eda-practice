package com.eda.stock.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.event.StockIncreased;
import com.eda.stock.event.StockRegistered;
import com.eda.stock.event.StockSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stocks;
    private final OutboxWriter outbox;

    @Transactional
    public void initialize(Long productId) {
        if (stocks.initialize(productId) == 1) {
            outbox.write(StockSnapshot.TOPIC, productId.toString(), new StockRegistered(productId, 0, 0));
        }
    }

    @Transactional
    public StockSnapshot increase(Long productId, int quantity) {
        Stock stock = stocks.findForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
        stock.increase(quantity);
        StockSnapshot snapshot = snapshotOf(stock);
        outbox.write(StockSnapshot.TOPIC, productId.toString(),
                new StockIncreased(productId, snapshot.quantity(), snapshot.version()));
        return snapshot;
    }

    @Transactional(readOnly = true)
    public StockSnapshot find(Long productId) {
        return stocks.findByProductId(productId)
                .map(this::snapshotOf)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
    }

    private StockSnapshot snapshotOf(Stock stock) {
        return new StockSnapshot(stock.getProductId(), stock.getQuantity(), stock.getVersion());
    }
}
