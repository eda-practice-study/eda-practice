package com.eda.order.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

// 4xx 는 다시 보내도 결과가 같으므로 연결·타임아웃·5xx 만 다시 시도한다
@Component
@RequiredArgsConstructor
public class StockRestoreRetrier {

    private final StockClient stockClient;

    @Retryable(
            includes = {ResourceAccessException.class, HttpServerErrorException.class},
            maxRetriesString = "${stock.restore.retry.max-retries}",
            delayString = "${stock.restore.retry.delay}",
            multiplierString = "${stock.restore.retry.multiplier}",
            maxDelayString = "${stock.restore.retry.max-delay}",
            jitterString = "${stock.restore.retry.jitter}")
    public void restore(String orderKey) {
        stockClient.restore(orderKey);
    }
}
