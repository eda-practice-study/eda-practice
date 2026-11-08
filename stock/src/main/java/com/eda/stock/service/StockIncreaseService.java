package com.eda.stock.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.api.StockIncreaseResponse;
import com.eda.stock.domain.RequestHistoryId;
import com.eda.stock.domain.Stock;
import com.eda.stock.infra.StockCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StockIncreaseService {

    private final StockWriteService stockWriteService;
    private final StockCache stockCache;

    public StockIncreaseResponse increaseForRequest(String key, Long productId, int quantity) {
        validateRequest(key, productId, quantity);
        Stock stock = stockWriteService.increase(key, productId, quantity);
        // 별도 빈의 트랜잭션 프록시가 커밋을 마친 뒤에만 Redis에 접근한다.
        Long availableQuantity = stockCache.synchronize(productId, stock.getQuantity(), stock.getVersion());
        return new StockIncreaseResponse(productId, stock.getQuantity(), availableQuantity);
    }

    private void validateRequest(String key, Long productId, int quantity) {
        if (key == null || key.isBlank() || key.length() > RequestHistoryId.MAX_KEY_LENGTH
                || productId == null || productId <= 0 || quantity < 1 || quantity > 10000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
