package com.eda.order.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.Topics;
import com.eda.common.message.order.OrderCreated;
import com.eda.common.message.order.ReservationCancelRequested;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.domain.Order;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.infrastructure.OrderRepository;
import com.eda.order.infrastructure.ProductSnapshotRepository;
import com.eda.order.infrastructure.StockClient;
import com.eda.order.infrastructure.StockReservationFailedException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

// 재고 예약 TCC 의 코디네이터
// Try     = stockClient.reserve (동기)
// Confirm = 주문과 함께 저장한 OrderCreated 이벤트를 stock 이 받아 처리
// Cancel  = ReservationCancelRequested 이벤트 (주문을 만들지 못했을 때)
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductSnapshotRepository productSnapshotRepository;
    private final OrderRepository orderRepository;
    private final StockClient stockClient;
    private final OutboxWriter outboxWriter;
    private final TransactionTemplate transactionTemplate;

    public Order create(Long memberId, List<OrderItem> items) {
        Order order = Order.create(memberId, toLineItems(items));
        UUID reservationId = order.getReservationId();

        try {
            stockClient.reserve(reservationId, order.getOrderLines());
        } catch (StockReservationFailedException e) {
            // 결과를 모르는 시도가 있었다면 stock 에서 늦게 예약될 수 있으므로 취소해 둔다
            if (e.isOutcomeUnknown()) {
                requestCancel(reservationId);
            }
            throw e;
        }

        try {
            return transactionTemplate.execute(status -> save(order));
        } catch (RuntimeException e) {
            log.warn("주문 저장 실패로 재고 예약을 취소함 memberId={}, reservationId={}", memberId, reservationId, e);
            requestCancel(reservationId);
            throw e;
        }
    }

    private Order save(Order order) {
        orderRepository.save(order);

        List<OrderCreated.Item> eventItems = order.getOrderLines().stream()
                .map(line -> new OrderCreated.Item(line.getProductId(), line.getQuantity()))
                .toList();
        outboxWriter.write(Topics.ORDER_EVENTS, String.valueOf(order.getId()),
                new OrderCreated(order.getId(), order.getReservationId(), order.getMemberId(), eventItems,
                        order.getTotalAmount()));

        return order;
    }

    // Cancel 요청을 outbox 에 남겨 확실히 전달한다. DB 도 실패하면 stock 을 직접 호출하고,
    // 그것도 실패하면 stock 의 만료 스케줄러가 예약을 취소한다
    private void requestCancel(UUID reservationId) {
        try {
            transactionTemplate.executeWithoutResult(status -> outboxWriter.write(
                    Topics.ORDER_EVENTS, reservationId.toString(), new ReservationCancelRequested(reservationId)));
            return;
        } catch (RuntimeException e) {
            log.warn("예약 취소 요청 저장 실패, stock 을 직접 호출함 reservationId={}", reservationId, e);
        }

        try {
            stockClient.cancel(reservationId);
        } catch (RuntimeException e) {
            log.error("예약 취소 실패 — 만료 스케줄러가 취소할 때까지 재고가 묶임 reservationId={}", reservationId, e);
        }
    }

    private List<Order.LineItem> toLineItems(List<OrderItem> items) {
        List<Long> productIds = items.stream().map(OrderItem::productId).toList();
        Map<Long, ProductSnapshot> snapshots = productSnapshotRepository.findAllByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(ProductSnapshot::getProductId, Function.identity()));

        return items.stream()
                .map(item -> {
                    ProductSnapshot snapshot = snapshots.get(item.productId());
                    if (snapshot == null) {
                        throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                    }
                    return snapshot.toLineItem(item.quantity());
                })
                .toList();
    }

    public record OrderItem(Long productId, int quantity) {
    }
}
