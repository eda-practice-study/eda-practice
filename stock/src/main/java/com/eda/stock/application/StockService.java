package com.eda.stock.application;

import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository repository;

    @Transactional
    public Stock initializeStock(Long productId) {
        Optional<Stock> existingStock = repository.findByProductId(productId);

        if (existingStock.isPresent()) {
            return existingStock.get();
        }
        Stock stock = Stock.createFor(productId);
        return repository.save(stock);
    }
}
