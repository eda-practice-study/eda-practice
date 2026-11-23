package com.eda.order.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.eda.common.message.order.OrderCreated;
import com.eda.common.message.order.ReservationCancelRequested;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.application.OrderService.OrderItem;
import com.eda.order.domain.Order;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.infrastructure.OrderRepository;
import com.eda.order.infrastructure.ProductSnapshotRepository;
import com.eda.order.infrastructure.StockClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// 주문 저장 실패 후 예약 취소 요청을 outbox 에 남기지 못한 경우 (DB 장애) 의 대체 경로
class OrderServiceCancelFallbackTest {

    private final ProductSnapshotRepository productSnapshotRepository = mock(ProductSnapshotRepository.class);
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final StockClient stockClient = mock(StockClient.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(productSnapshotRepository, orderRepository, stockClient, outboxWriter,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
        given(productSnapshotRepository.findAllByProductIdIn(anyCollection()))
                .willReturn(List.of(ProductSnapshot.of(1L, "키보드", BigDecimal.valueOf(30_000))));
        // 주문 저장 실패
        willThrow(new IllegalStateException("DB 오류")).given(outboxWriter)
                .write(anyString(), anyString(), any(OrderCreated.class));
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));
        // 취소 요청 outbox 저장도 실패
        willThrow(new IllegalStateException("outbox 오류")).given(outboxWriter)
                .write(anyString(), anyString(), any(ReservationCancelRequested.class));
    }

    @Test
    @DisplayName("취소 요청을 outbox 에 저장하지 못하면 stock 의 취소 API 를 직접 호출한다")
    void cancelDirectly() {
        // given
        ArgumentCaptor<UUID> reservationId = ArgumentCaptor.forClass(UUID.class);
        willDoNothing().given(stockClient).reserve(reservationId.capture(), anyList());

        // when & then
        assertThatThrownBy(() -> orderService.create(1L, List.of(new OrderItem(1L, 2))))
                .isInstanceOf(IllegalStateException.class).hasMessage("DB 오류");
        then(stockClient).should().cancel(reservationId.getValue());
    }

    @Test
    @DisplayName("직접 호출까지 실패해도 원래 예외만 던진다 (남은 예약은 stock 의 만료 스케줄러가 취소)")
    void cancelFailureDoesNotHideCause() {
        // given
        willThrow(new IllegalStateException("stock 오류")).given(stockClient).cancel(any());

        // when & then
        assertThatThrownBy(() -> orderService.create(1L, List.of(new OrderItem(1L, 2))))
                .isInstanceOf(IllegalStateException.class).hasMessage("DB 오류");
    }
}
