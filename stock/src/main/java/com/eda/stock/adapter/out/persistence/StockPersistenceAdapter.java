package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.LoadStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class StockPersistenceAdapter implements LoadStockPort, SaveStockPort {

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
    public boolean existsByProductId(Long productId) {
        return stockRepository.existsByProductId(productId);
    }
}
