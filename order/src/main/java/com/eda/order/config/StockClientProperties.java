package com.eda.order.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// maxAttempts = 재고 예약(Try) 결과를 모를 때(타임아웃 · 5xx 등) 같은 reservationId 로 보내는 최대 횟수 (첫 시도 포함)
@ConfigurationProperties(prefix = "order.stock-client")
public record StockClientProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Integer maxAttempts,
        Duration retryBackoff
) {

    public StockClientProperties {
        baseUrl = baseUrl == null ? "http://localhost:8084" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
        maxAttempts = maxAttempts == null ? 3 : maxAttempts;
        retryBackoff = retryBackoff == null ? Duration.ofMillis(100) : retryBackoff;
    }
}
