package com.eda.stock.application.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.in.StockAddResult;
import com.eda.stock.application.port.in.AddStockUseCase;
import com.eda.stock.application.port.in.CreateStockUseCase;
import com.eda.stock.application.port.out.LoadStockPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class StockService implements AddStockUseCase, CreateStockUseCase {

    private final SaveStockPort saveStockPort;
    private final LoadStockPort loadStockPort;

    @Override
    public void createFor(Long productId) {
        // 멱등성 처리
        if (loadStockPort.existsByProductId(productId)) {
            log.warn("재고가 이미 존재 (중복 수신) productId={}", productId);
            return;
        }

        Stock stock = saveStockPort.save(Stock.createFor(productId));
        log.info("재고 생성 완료 productId={}, stockId={}, quantity={}", productId, stock.getId(), stock.getQuantity());
    }

    @Override
    public StockAddResult add(Long productId, int quantity) {
        Stock stock = loadStockPort.findByProductId(productId)
                .orElseThrow(() -> {
                    log.warn("재고 등록 실패 - 재고 없음 productId={}, quantity={}", productId, quantity);
                    return new BusinessException(ErrorCode.STOCK_NOT_FOUND);
                });

        int before = stock.getQuantity();
        stock.add(quantity);
        log.info("재고 등록 완료 productId={}, quantity: {} -> {}", productId, before, stock.getQuantity());
        return new StockAddResult(productId, quantity, stock.getQuantity());
    }
}
