package com.eda.order.infrastructure;

import com.eda.common.exception.ErrorCode;
import com.eda.order.config.StockClientProperties;
import com.eda.order.domain.OrderLine;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// stock 의 재고 예약 TCC 내부 API (/internal/stocks/reservations/**) 호출. order 는 Redis 에 직접 접근하지 않는다
@Slf4j
@Component
@RequiredArgsConstructor
public class StockClient {

    private final RestClient stockRestClient;
    private final StockClientProperties properties;

    // TCC Try. stock 이 reservationId 로 멱등 처리하므로, 결과를 모르면 같은 ID 로 다시 보내 확인한다
    // - 성공 응답: 이번 시도든 앞선 시도든 예약됨
    // - 4xx: 이번 시도에서 아무것도 바뀌지 않음 (재고 부족 · 취소된 ID · 잘못된 요청)
    // - 타임아웃 · 연결 오류 · 5xx: 실행됐는지 모름 → 재시도
    public void reserve(UUID reservationId, List<OrderLine> orderLines) {
        ReserveRequest body = ReserveRequest.of(reservationId, orderLines);
        boolean outcomeUnknown = false;

        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                stockRestClient.post()
                        .uri("/internal/stocks/reservations")
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (HttpClientErrorException e) {
                throw new StockReservationFailedException(toErrorCode(e), outcomeUnknown);
            } catch (RestClientException e) {
                outcomeUnknown = true;
                log.warn("재고 예약 결과를 모름 attempt={}/{}, reservationId={}",
                        attempt, properties.maxAttempts(), reservationId, e);
                if (attempt < properties.maxAttempts()) {
                    sleep(properties.retryBackoff().toMillis() << (attempt - 1));
                }
            }
        }

        throw new StockReservationFailedException(ErrorCode.STOCK_SERVICE_UNAVAILABLE, true);
    }

    // TCC Cancel. 예약이 없어도 stock 이 취소 표식을 남기므로 언제 호출해도 안전하다
    public void cancel(UUID reservationId) {
        stockRestClient.post()
                .uri("/internal/stocks/reservations/{reservationId}/cancel", reservationId)
                .retrieve()
                .toBodilessEntity();
    }

    private ErrorCode toErrorCode(HttpClientErrorException e) {
        if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
            return ErrorCode.OUT_OF_STOCK;
        }
        log.error("재고 예약 요청이 거부됨 status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        return ErrorCode.STOCK_SERVICE_UNAVAILABLE;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record ReserveRequest(UUID reservationId, List<Item> items) {

        static ReserveRequest of(UUID reservationId, List<OrderLine> orderLines) {
            return new ReserveRequest(reservationId, orderLines.stream()
                    .map(line -> new Item(line.getProductId(), line.getQuantity()))
                    .toList());
        }

        private record Item(Long productId, int quantity) {
        }
    }
}
