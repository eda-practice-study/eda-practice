package com.eda.order.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.order.application.OrderService.OrderItem;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderLine;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.infrastructure.OrderRepository;
import com.eda.order.infrastructure.ProductSnapshotRepository;
import com.eda.order.infrastructure.StockClient;
import com.eda.order.infrastructure.StockReservationFailedException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// 주문 생성 = 재고 예약 TCC 의 코디네이터. stock 호출은 mock 으로 바꾸고 주문 · outbox 저장 결과를 본다
@SpringBootTest(properties = "outbox.enabled=false")
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductSnapshotRepository productSnapshotRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    @MockitoBean
    private StockClient stockClient;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        orderRepository.deleteAll();
        productSnapshotRepository.deleteAll();
        productSnapshotRepository.save(ProductSnapshot.of(1L, "키보드", BigDecimal.valueOf(30_000)));
        productSnapshotRepository.save(ProductSnapshot.of(2L, "마우스", BigDecimal.valueOf(15_000)));
    }

    @Test
    @DisplayName("재고를 예약한 뒤 주문과 주문 생성 이벤트를 함께 저장한다")
    void create() {
        // when
        Order order = orderService.create(1L, List.of(new OrderItem(1L, 2), new OrderItem(2L, 1)));

        // then — 상품명 · 단가는 스냅샷 값
        assertThat(order.getId()).isNotNull();
        assertThat(order.getTotalAmount()).isEqualByComparingTo("75000");
        assertThat(order.getOrderLines()).extracting(OrderLine::getProductName).containsExactly("키보드", "마우스");
        assertThat(orderRepository.count()).isEqualTo(1);

        // then — Try 는 주문의 reservationId 로 호출
        ArgumentCaptor<UUID> reservationId = ArgumentCaptor.forClass(UUID.class);
        then(stockClient).should().reserve(reservationId.capture(), anyList());
        assertThat(reservationId.getValue()).isEqualTo(order.getReservationId());

        // then — Confirm 트리거인 OrderCreated 에 reservationId 를 담는다
        List<OutboxMessage> events = events("OrderCreated");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMessageKey()).isEqualTo(String.valueOf(order.getId()));
        assertThat(events.get(0).getPayload())
                .contains("\"orderId\":" + order.getId())
                .contains("\"reservationId\":\"" + order.getReservationId() + "\"");
        assertThat(events("ReservationCancelRequested")).isEmpty();
    }

    @Test
    @DisplayName("재고가 부족하면 주문을 저장하지 않고, 바뀐 것이 없으니 취소도 요청하지 않는다")
    void outOfStock() {
        // given
        willThrow(new StockReservationFailedException(ErrorCode.OUT_OF_STOCK, false))
                .given(stockClient).reserve(any(), anyList());

        // when & then
        assertThatThrownBy(() -> orderService.create(1L, List.of(new OrderItem(1L, 2))))
                .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
        assertThat(orderRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("Try 결과를 모른 채 실패하면 주문을 저장하지 않고 예약 취소를 요청한다")
    void outcomeUnknown() {
        // given
        ArgumentCaptor<UUID> reservationId = ArgumentCaptor.forClass(UUID.class);
        willThrow(new StockReservationFailedException(ErrorCode.STOCK_SERVICE_UNAVAILABLE, true))
                .given(stockClient).reserve(reservationId.capture(), anyList());

        // when & then
        assertThatThrownBy(() -> orderService.create(1L, List.of(new OrderItem(1L, 2))))
                .extracting("errorCode").isEqualTo(ErrorCode.STOCK_SERVICE_UNAVAILABLE);
        assertThat(orderRepository.count()).isZero();
        assertThat(events("OrderCreated")).isEmpty();
        assertCancelRequested(reservationId.getValue());
    }

    @Test
    @DisplayName("재시도 중 결과를 모르는 시도가 있었다면, 마지막이 재고 부족이어도 예약 취소를 요청한다")
    void outOfStockAfterUnknown() {
        // given
        ArgumentCaptor<UUID> reservationId = ArgumentCaptor.forClass(UUID.class);
        willThrow(new StockReservationFailedException(ErrorCode.OUT_OF_STOCK, true))
                .given(stockClient).reserve(reservationId.capture(), anyList());

        // when & then
        assertThatThrownBy(() -> orderService.create(1L, List.of(new OrderItem(1L, 2))))
                .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
        assertCancelRequested(reservationId.getValue());
    }

    @Test
    @DisplayName("주문 저장에 실패하면 주문 · 주문 생성 이벤트를 롤백하고 예약 취소를 요청한다")
    void saveFailure() {
        // given
        ArgumentCaptor<UUID> reservationId = ArgumentCaptor.forClass(UUID.class);
        willDoNothing().given(stockClient).reserve(reservationId.capture(), anyList());
        willThrow(new IllegalStateException("DB 오류")).given(orderRepository).save(any(Order.class));

        // when & then
        assertThatThrownBy(() -> orderService.create(1L, List.of(new OrderItem(1L, 2))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(orderRepository.count()).isZero();
        assertThat(events("OrderCreated")).isEmpty();
        assertCancelRequested(reservationId.getValue());
        then(stockClient).should(never()).cancel(any());
    }

    @Test
    @DisplayName("상품 스냅샷이 없으면 재고를 예약하지 않고 PRODUCT_NOT_FOUND")
    void productNotFound() {
        assertThatThrownBy(() -> orderService.create(1L, List.of(new OrderItem(1L, 1), new OrderItem(99L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        then(stockClient).shouldHaveNoInteractions();
        assertThat(orderRepository.count()).isZero();
    }

    private void assertCancelRequested(UUID reservationId) {
        List<OutboxMessage> cancels = events("ReservationCancelRequested");
        assertThat(cancels).hasSize(1);
        assertThat(cancels.get(0).getMessageKey()).isEqualTo(reservationId.toString());
        assertThat(cancels.get(0).getPayload()).isEqualTo("{\"reservationId\":\"" + reservationId + "\"}");
    }

    private List<OutboxMessage> events(String messageType) {
        return outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType().equals(messageType))
                .toList();
    }
}
