package com.eda.order.stock;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockClient {

    private final RestClient stockRestClient;

    public record Item(Long productId, int quantity) {
    }

    private record DecreaseRequest(String orderKey, List<Item> items) {
    }

    private record RestoreRequest(String orderKey) {
    }

    public void decrease(String orderKey, List<Item> items) {
        try {
            stockRestClient.post().uri("/internal/stocks/decrease")
                    .body(new DecreaseRequest(orderKey, items))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict e) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        } catch (HttpServerErrorException e) {
            log.warn("stock 차감 실패(5xx), 차감됐을 수 있음 orderKey={} items={} status={}",
                    orderKey, items, e.getStatusCode());
            throw new StockCallException(true);
        } catch (HttpClientErrorException e) {
            log.error("stock 차감 요청 거부 orderKey={} items={} status={}", orderKey, items, e.getStatusCode());
            throw new StockCallException(false);
        } catch (ResourceAccessException e) {
            if (notSent(e)) {
                log.warn("stock 연결 실패 orderKey={} items={}", orderKey, items, e);
                throw new StockCallException(false);
            }
            log.warn("stock 응답 없음(read timeout 등), 차감됐을 수 있음 orderKey={} items={}", orderKey, items, e);
            throw new StockCallException(true);
        }
    }

    public void restore(String orderKey) {
        stockRestClient.post().uri("/internal/stocks/restore")
                .body(new RestoreRequest(orderKey))
                .retrieve()
                .toBodilessEntity();
    }

    // 연결을 맺지 못한 경우만 요청이 닿지 않았다고 확신할 수 있다. HttpConnectTimeoutException 은 HttpTimeoutException 의 하위라 먼저 본다.
    private boolean notSent(ResourceAccessException e) {
        Throwable cause = e.getCause();
        return cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException;
    }
}
