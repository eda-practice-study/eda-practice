package com.eda.stock.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.stock.StockIncreased;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.application.dto.StockIncreaseResult;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockCommandService {

    private final StockRepository stockRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public StockIncreaseResult increase(Long productId, int quantity) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        // 변경 감지로 재고를 반영한다. Outbox 실패 시 재고 변경도 롤백된다.
        stock.increase(quantity);
        outboxWriter.write(
                "stock.events",
                stock.getProductId().toString(),
                new StockIncreased(stock.getProductId(), stock.getQuantity())
        );

        return new StockIncreaseResult(stock.getProductId(), stock.getQuantity());
    }
}
