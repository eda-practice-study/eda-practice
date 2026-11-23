package com.eda.stock.event;

import com.eda.common.message.Message;
import com.eda.common.message.Topics;
import com.eda.common.message.order.OrderCreated;
import com.eda.common.message.order.ReservationCancelRequested;
import com.eda.stock.application.StockService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// 같은 그룹에서 리스너를 둘로 나누면 파티션이 나뉘어 메시지를 놓치므로, 한 곳에서 타입별로 나눈다
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final StockService stockService;

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "stock-service")
    public void handle(ConsumerRecord<String, String> record) {
        if (Message.isType(record, OrderCreated.class)) {
            handleOrderCreated(Message.from(record, OrderCreated.class).payload());
        } else if (Message.isType(record, ReservationCancelRequested.class)) {
            stockService.cancel(Message.from(record, ReservationCancelRequested.class).payload().reservationId());
        }
    }

    // TCC Confirm — DB 차감(Redis → DB 순서의 두 번째)이 커밋된 뒤 Redis 예약을 확정한다
    // 둘 다 멱등이라, 중간에 실패해 다시 받아도 DB 는 한 번만 차감하고 예약도 한 번만 확정한다
    private void handleOrderCreated(OrderCreated event) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        event.items().forEach(item -> quantities.put(item.productId(), item.quantity()));

        stockService.decreaseForOrder(event.orderId(), quantities);
        stockService.confirm(event.orderId(), event.reservationId());
    }
}
