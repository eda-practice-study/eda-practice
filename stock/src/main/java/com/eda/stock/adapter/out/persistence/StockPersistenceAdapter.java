package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.LoadStockForUpdatePort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class StockPersistenceAdapter implements
        SaveStockPort,
        LoadStockForUpdatePort {

    private final StockJpaRepository stockJpaRepository;

    @Override
    public Stock save(Stock stock) {
        return stockJpaRepository.save(stock);
    }

    @Override
    public Optional<Stock> loadByProductIdForUpdate(Long productId) {
        return stockJpaRepository.findByProductIdForUpdate(productId);
    }

    @Override
    public List<Stock> loadAllByProductIdsForUpdate(List<Long> productIds) {
        return stockJpaRepository.findAllByProductIdInForUpdate(productIds);
    }
}
