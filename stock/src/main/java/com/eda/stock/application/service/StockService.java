package com.eda.stock.application.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.in.AddStockCommand;
import com.eda.stock.application.port.in.AddStockUseCase;
import com.eda.stock.application.port.in.CreateStockCommand;
import com.eda.stock.application.port.in.CreateStockUseCase;
import com.eda.stock.application.port.out.LoadStockForUpdatePort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService implements
        CreateStockUseCase,
        AddStockUseCase {

    private final SaveStockPort saveStockPort;
    private final LoadStockForUpdatePort loadStockForUpdatePort;

    @Override
    @Transactional
    public Stock create(CreateStockCommand command) {
        Stock stock = Stock.createFor(command.productId());

        return saveStockPort.save(stock);
    }

    @Override
    @Transactional
    public Stock add(AddStockCommand command) {
        Stock stock = loadStockForUpdatePort
                .loadByProductIdForUpdate(command.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        stock.add(command.quantity());
        return saveStockPort.save(stock);
    }
}
