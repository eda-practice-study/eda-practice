package com.eda.stock.application.service;

import com.eda.stock.application.port.in.CreateStockUseCase;
import com.eda.stock.application.port.out.LoadStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StockCommandService implements CreateStockUseCase {

    private final LoadStockPort loadStockPort;
    private final SaveStockPort saveStockPort;

    @Override
    public void create(Long productId) {
        if (loadStockPort.existsByProductId(productId)) {
            log.info("이미 재고가 존재해 생성을 건너뜁니다. productId={}", productId);
            return;
        }

        saveStockPort.save(Stock.createFor(productId));
        log.info("재고를 생성했습니다. productId={}", productId);
    }
}
