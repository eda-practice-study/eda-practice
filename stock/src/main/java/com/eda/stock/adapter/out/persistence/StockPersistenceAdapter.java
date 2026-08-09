package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.LoadStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockPersistenceAdapter implements LoadStockPort, SaveStockPort {

    private final StockJpaRepository stockJpaRepository;

    @Override
    public boolean existsByProductId(Long productId) {
        return stockJpaRepository.existsByProductId(productId);
    }

    @Override
    public Optional<Stock> findByProductId(Long productId) {
        return stockJpaRepository.findByProductId(productId);
    }

    @Override
    public Stock save(Stock stock) {
        return stockJpaRepository.save(stock);
    }
}
