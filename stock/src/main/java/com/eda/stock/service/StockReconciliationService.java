package com.eda.stock.service;

import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.infra.StockCache;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class StockReconciliationService {

    private final StockRepository stockRepository;
    private final StockCache stockCache;
    private final PageRequest page;

    public StockReconciliationService(StockRepository stockRepository, StockCache stockCache,
            @Value("${stock.reconciliation.batch-size:100}") int batchSize) {
        this.stockRepository = stockRepository;
        this.stockCache = stockCache;
        this.page = PageRequest.of(0, batchSize);
    }

    public void reconcile() {
        long lastProductId = 0;
        while (true) {
            List<Stock> stocks = stockRepository.findByProductIdGreaterThanOrderByProductIdAsc(lastProductId, page);
            if (stocks.isEmpty()) {
                return;
            }
            for (Stock stock : stocks) {
                stockCache.synchronize(stock.getProductId(), stock.getQuantity(), stock.getVersion());
            }
            lastProductId = stocks.getLast().getProductId();
        }
    }
}
