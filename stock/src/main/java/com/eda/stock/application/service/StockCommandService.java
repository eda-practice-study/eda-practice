package com.eda.stock.application.service;

import com.eda.stock.adapter.in.web.dto.AddStockRequest;
import com.eda.stock.application.port.in.AddStockUseCase;
import com.eda.stock.application.port.in.CreateStockUseCase;
import com.eda.stock.application.port.out.LoadStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockCommandService implements CreateStockUseCase, AddStockUseCase {

    private final SaveStockPort saveStockPort;
    private final LoadStockPort loadStockPort;

    @Override
    @Transactional
    public Stock register(Long productId) {
        Stock stock = Stock.createFor(productId);
        // 재고 초기 등록 = 0
        return saveStockPort.save(stock);
    }


    @Override
    @Transactional
    public void add(AddStockRequest addStockRequest) {
        Stock stock = loadStockPort.load(addStockRequest.productId());
        stock.add(addStockRequest.quantity());
    }
}
