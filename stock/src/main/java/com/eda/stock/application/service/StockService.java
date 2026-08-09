package com.eda.stock.application.service;

import com.eda.stock.application.port.in.CreateStockCommand;
import com.eda.stock.application.port.in.CreateStockUseCase;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService implements CreateStockUseCase {

    private final SaveStockPort saveStockPort;

    @Override
    @Transactional
    public Stock create(CreateStockCommand command) {
        Stock stock = Stock.createFor(command.productId());

        return saveStockPort.save(stock);
    }
}
