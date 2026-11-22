package com.eda.stock.event;

import com.eda.common.message.Message;
import com.eda.common.message.MessageHeader;
import com.eda.common.message.order.OrderCreated;
import com.eda.stock.application.StockDecreaseService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class OrderCreatedListener {

    private static final String ORDER_CREATED = OrderCreated.class.getSimpleName();

    private final StockDecreaseService stockDecreaseService;
    private final JsonMapper jsonMapper;

    // order.events 에는 ProductSnapshotRegistered 도 오므로 타입을 먼저 본다
    @KafkaListener(topics = "order.events", groupId = "stock-service")
    public void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader(MessageHeader.MESSAGE_TYPE);
        if (type == null || !ORDER_CREATED.equals(new String(type.value(), StandardCharsets.UTF_8))) {
            return;
        }

        Message<OrderCreated> message = Message.from(record, OrderCreated.class, jsonMapper);
        stockDecreaseService.decrease(message.messageId(), message.payload());
    }
}
