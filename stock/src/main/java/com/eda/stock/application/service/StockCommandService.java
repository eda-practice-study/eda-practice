package com.eda.stock.application.service;

import com.eda.stock.application.port.in.CreateStockUseCase;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockCommandService implements CreateStockUseCase {

    private final SaveStockPort saveStockPort;

    @Override
    @Transactional
    public Stock register(Long productId) {
        Stock stock = Stock.createFor(productId);
        // 재고 초기 등록 = 0
        return saveStockPort.save(stock);
    }
}
