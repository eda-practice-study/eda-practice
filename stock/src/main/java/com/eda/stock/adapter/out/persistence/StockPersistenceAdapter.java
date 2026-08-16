package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.FindStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StockPersistenceAdapter implements FindStockPort, SaveStockPort {

    private final StockJpaRepository repository;

    @Override
    public Optional<Stock> findByProductId(Long productId) {
        return repository.findByProductId(productId);
    }

    @Override
    public Stock save(Stock stock) {
        return repository.save(stock);
    }
}
