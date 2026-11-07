package com.eda.stock.event;

import com.eda.common.event.ProductCreated;
import com.eda.common.message.Message;
import com.eda.common.message.MessageHeader;
import com.eda.stock.redis.StockCache;
import com.eda.stock.service.StockService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final StockService stocks;
    private final StockCache cache;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = "product.events", groupId = "stock-initialization")
    public void initialize(ConsumerRecord<String, String> record) {
        var type = record.headers().lastHeader(MessageHeader.MESSAGE_TYPE);
        if (type != null && "ProductCreated".equals(new String(type.value(), StandardCharsets.UTF_8))) {
            stocks.initialize(Message.from(record, ProductCreated.class, jsonMapper).payload().productId());
        }
    }

    @KafkaListener(topics = StockSnapshot.TOPIC, groupId = "stock-cache")
    public void updateCache(ConsumerRecord<String, String> record) {
        var type = record.headers().lastHeader(MessageHeader.MESSAGE_TYPE);
        if (type == null) {
            return;
        }
        switch (new String(type.value(), StandardCharsets.UTF_8)) {
            case "StockRegistered", "StockIncreased" ->
                    cache.apply(Message.from(record, StockSnapshot.class, jsonMapper).payload());
            default -> {
            }
        }
    }
}
