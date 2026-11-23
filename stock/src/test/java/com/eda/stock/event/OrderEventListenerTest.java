package com.eda.stock.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.MessageHeader;
import com.eda.common.message.Topics;
import com.eda.common.message.order.OrderCreated;
import com.eda.common.message.order.ProductSnapshotRegistered;
import com.eda.common.message.order.ReservationCancelRequested;
import com.eda.stock.application.StockService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

class OrderEventListenerTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private final StockService stockService = mock(StockService.class);
    private final OrderEventListener listener = new OrderEventListener(stockService);

    @Test
    @DisplayName("주문 생성 이벤트를 받으면 DB 재고를 차감한 뒤 Redis 예약을 확정한다 (Confirm)")
    void orderCreated() {
        // given
        UUID reservationId = UUID.randomUUID();
        OrderCreated event = new OrderCreated(100L, reservationId, 1L,
                List.of(new OrderCreated.Item(1L, 2), new OrderCreated.Item(2L, 1)), BigDecimal.valueOf(75_000));

        // when
        listener.handle(record(event));

        // then
        InOrder inOrder = inOrder(stockService);
        inOrder.verify(stockService).decreaseForOrder(100L, Map.of(1L, 2, 2L, 1));
        inOrder.verify(stockService).confirm(100L, reservationId);
    }

    @Test
    @DisplayName("DB 차감에 실패하면 예약을 확정하지 않는다 (이벤트를 다시 받아 처리)")
    void doNotConfirmWhenDecreaseFails() {
        // given
        OrderCreated event = new OrderCreated(100L, UUID.randomUUID(), 1L,
                List.of(new OrderCreated.Item(1L, 2)), BigDecimal.valueOf(60_000));
        willThrow(new BusinessException(ErrorCode.OUT_OF_STOCK)).given(stockService).decreaseForOrder(any(), anyMap());

        // when & then
        assertThatThrownBy(() -> listener.handle(record(event))).isInstanceOf(BusinessException.class);
        then(stockService).should(never()).confirm(any(), any());
    }

    @Test
    @DisplayName("예약 취소 요청을 받으면 예약을 취소한다 (Cancel)")
    void reservationCancelRequested() {
        // given
        UUID reservationId = UUID.randomUUID();

        // when
        listener.handle(record(new ReservationCancelRequested(reservationId)));

        // then
        then(stockService).should().cancel(reservationId);
        then(stockService).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("다른 타입의 이벤트는 무시한다")
    void ignoreOtherTypes() {
        listener.handle(record(new ProductSnapshotRegistered(1L, "키보드", BigDecimal.valueOf(30_000))));

        then(stockService).shouldHaveNoInteractions();
    }

    private ConsumerRecord<String, String> record(Object payload) {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(Topics.ORDER_EVENTS, 0, 0L, "key", JSON_MAPPER.writeValueAsString(payload));
        record.headers().add(MessageHeader.MESSAGE_ID, bytes(UUID.randomUUID().toString()));
        record.headers().add(MessageHeader.MESSAGE_TYPE, bytes(payload.getClass().getSimpleName()));
        record.headers().add(MessageHeader.OCCURRED_AT, bytes(Instant.now().toString()));
        return record;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
