package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.LoadLockedStockPort;
import com.eda.stock.application.port.out.LoadStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class StockPersistenceAdapter implements LoadStockPort, LoadLockedStockPort, SaveStockPort {

    private final StockJpaRepository stockRepository;

    @Override
    public Stock save(Stock stock) {
        return stockRepository.save(stock);
    }

    @Override
    public Optional<Stock> findByProductId(Long productId) {
        return stockRepository.findByProductId(productId);
    }

    @Override
    public Optional<Stock> findByProductIdForUpdate(Long productId) {
        return stockRepository.findByProductIdForUpdate(productId);
    }

    @Override
    public List<Stock> findAllByProductIdsForUpdate(Collection<Long> productIds) {
        return stockRepository.findAllByProductIdsForUpdate(productIds);
    }

    @Override
    public boolean existsByProductId(Long productId) {
        return stockRepository.existsByProductId(productId);
    }
}
