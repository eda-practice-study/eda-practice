package com.eda.stock.adapter.out.persistence;

import com.eda.stock.application.port.out.LoadStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockPersistenceAdapter implements SaveStockPort, LoadStockPort {

    private final StockJpaRepository stockJpaRepository;

    @Override
    public Stock save(Stock stock) {
        return stockJpaRepository.save(stock);
    }

    @Override
    public Stock load(Long productId) {
        return stockJpaRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않는 상품번호 입니다."));
    }
}
