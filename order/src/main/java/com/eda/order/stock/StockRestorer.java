package com.eda.order.stock;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// 주문 응답을 막지 않도록 restore 는 별도 스레드에서 다시 시도한다. 메모리 안에서만 기다리므로 order 가 죽으면 사라진다.
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRestorer {

    private final StockRestoreRetrier retrier;

    @Async("restoreExecutor")
    public void restore(String orderKey, List<StockClient.Item> items) {
        try {
            retrier.restore(orderKey);
        } catch (Exception e) {
            log.error("재고 복원을 끝내 실패했습니다 orderKey={} items={}", orderKey, items, e);
        }
    }
}
