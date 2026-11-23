package com.eda.stock.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// timeout    = 이 시간 넘게 확정 · 취소되지 않은 예약은 자동 취소한다 (주문 이벤트가 이보다 늦게 오면 과대 판매 위험)
// record-ttl = 확정 · 취소된 예약 기록을 남겨 두는 시간. 이 안에 늦게 도착한 Try 는 기록을 보고 다시 차감하지 않는다
@ConfigurationProperties(prefix = "stock.reservation")
public record StockReservationProperties(
        @DefaultValue("true") boolean expireEnabled,
        @DefaultValue("30s") Duration expireInterval,
        @DefaultValue("10m") Duration timeout,
        @DefaultValue("1d") Duration recordTtl,
        @DefaultValue("100") int expireBatchSize
) {
}
