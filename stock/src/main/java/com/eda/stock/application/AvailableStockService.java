package com.eda.stock.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.redis.AvailableStockStore;
import com.eda.stock.redis.AvailableStockStore.DecreaseResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailableStockService {

    private final AvailableStockStore availableStockStore;

    public record Item(Long productId, int quantity) {
    }

    public void decrease(String orderKey, List<Item> items) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (Item item : items) {
            if (quantities.putIfAbsent(item.productId(), item.quantity()) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }

        DecreaseResult result = availableStockStore.decreaseAll(orderKey, quantities);
        if (result == DecreaseResult.CANCELLED) {
            log.warn("이미 취소된 주문 키의 차감 요청 orderKey={}", orderKey);
        }
        if (result != DecreaseResult.DECREASED) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
    }

    public void restore(String orderKey) {
        availableStockStore.restore(orderKey);
    }
}
