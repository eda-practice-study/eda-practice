package com.eda.stock.application.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.in.AddStockCommand;
import com.eda.stock.application.port.in.AddStockUseCase;
import com.eda.stock.application.port.in.CreateInitialStockUseCase;
import com.eda.stock.application.port.out.FindStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class StockCommandService implements CreateInitialStockUseCase, AddStockUseCase {

    private final FindStockPort findStockPort;
    private final SaveStockPort saveStockPort;

    @Override
    public void create(Long productId) {
        if(findStockPort.findByProductId(productId).isPresent()) {
            return;
        }

        Stock stock = Stock.createFor(productId);
        saveStockPort.save(stock);
    }

    @Override
    public int add(AddStockCommand command) {
        Stock stock = findStockPort.findByProductId(command.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        stock.add(command.quantity());

        Stock savedStock = saveStockPort.save(stock);
        return savedStock.getQuantity();
    }
}
