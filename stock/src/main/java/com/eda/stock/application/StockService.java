package com.eda.stock.application;

import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository repository;

    public void initializeStock(Long productId) {
        if (repository.existByProductId(productId)) {
            return;
        }

        Stock stock = Stock.createFor(productId);
        repository.save(stock);
    }
}
