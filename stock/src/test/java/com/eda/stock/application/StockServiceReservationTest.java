package com.eda.stock.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.eda.common.exception.ErrorCode;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.config.StockReservationProperties;
import com.eda.stock.infrastructure.ProcessedOrderRepository;
import com.eda.stock.infrastructure.StockRedisRepository;
import com.eda.stock.infrastructure.StockRepository;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// Try 결과 코드를 API 응답(예외)으로 바꾸는 부분
class StockServiceReservationTest {

    private final StockRedisRepository stockRedisRepository = mock(StockRedisRepository.class);
    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockService = new StockService(
                mock(StockRepository.class),
                stockRedisRepository,
                mock(ProcessedOrderRepository.class),
                mock(OutboxWriter.class),
                new StockReservationProperties(true, Duration.ofSeconds(30), Duration.ofMinutes(10),
                        Duration.ofDays(1), 100));
    }

    @Test
    @DisplayName("예약하면 예외 없이 끝난다")
    void reserve() {
        given(stockRedisRepository.reserve(any(), anyMap(), anyLong())).willReturn(0L);

        assertThatCode(() -> stockService.reserve(UUID.randomUUID(), Map.of(1L, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("재고가 부족하면 OUT_OF_STOCK")
    void outOfStock() {
        // given
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        quantities.put(1L, 1);
        quantities.put(2L, 5);
        given(stockRedisRepository.reserve(any(), anyMap(), anyLong())).willReturn(2L);

        // when & then
        assertThatThrownBy(() -> stockService.reserve(UUID.randomUUID(), quantities))
                .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("이미 취소된 예약 ID 면 RESERVATION_CANCELLED")
    void cancelled() {
        given(stockRedisRepository.reserve(any(), anyMap(), anyLong())).willReturn(-1L);

        assertThatThrownBy(() -> stockService.reserve(UUID.randomUUID(), Map.of(1L, 1)))
                .extracting("errorCode").isEqualTo(ErrorCode.RESERVATION_CANCELLED);
    }

    @Test
    @DisplayName("수량이 0 이하면 Redis 를 호출하지 않고 INVALID_STOCK_QUANTITY")
    void invalidQuantity() {
        assertThatThrownBy(() -> stockService.reserve(UUID.randomUUID(), Map.of(1L, 0)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STOCK_QUANTITY);
        then(stockRedisRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("취소는 예약 기록 보관 시간을 함께 넘긴다")
    void cancel() {
        UUID id = UUID.randomUUID();
        given(stockRedisRepository.cancel(id, Duration.ofDays(1))).willReturn(1L);

        stockService.cancel(id);

        then(stockRedisRepository).should().cancel(id, Duration.ofDays(1));
    }
}
