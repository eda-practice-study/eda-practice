package com.eda.stock.application;

import com.eda.stock.config.StockReservationProperties;
import com.eda.stock.infrastructure.StockRedisRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 확정도 취소도 되지 않고 남은 예약을 취소한다 (Cancel 요청까지 유실된 경우의 안전망)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "stock.reservation", name = "expire-enabled", havingValue = "true", matchIfMissing = true)
public class StockReservationExpirer {

    private final StockRedisRepository stockRedisRepository;
    private final StockService stockService;
    private final StockReservationProperties properties;

    @Scheduled(fixedDelayString = "${stock.reservation.expire-interval:30s}")
    @SchedulerLock(name = "stock-reservation-expire", lockAtMostFor = "PT5M")
    public void expire() {
        long reservedBefore = System.currentTimeMillis() - properties.timeout().toMillis();
        Set<String> expired = stockRedisRepository.findReservedBefore(reservedBefore, properties.expireBatchSize());
        if (expired == null) {
            return;
        }

        for (String reservationId : expired) {
            try {
                log.warn("만료된 재고 예약 취소 reservationId={}", reservationId);
                stockService.cancel(UUID.fromString(reservationId));
            } catch (RuntimeException e) {
                log.warn("만료된 재고 예약 취소 실패 reservationId={}", reservationId, e);
            }
        }
    }
}
