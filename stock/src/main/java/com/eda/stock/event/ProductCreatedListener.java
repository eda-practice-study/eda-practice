package com.eda.stock.event;

import com.eda.common.message.Message;
import com.eda.common.message.product.ProductCreated;
import com.eda.stock.application.StockRegistrationService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ProductCreatedListener {

    private final StockRegistrationService stockRegistrationService;
    private final JsonMapper jsonMapper;

    // DB 저장과 Redis 반영을 트랜잭션 둘로 나눈다. Redis 가 실패해도 재고 행은 남고, 재전송되면 Redis 만 다시 한다.
    @KafkaListener(topics = "product.events", groupId = "stock-service")
    public void on(ConsumerRecord<String, String> record) {
        Message<ProductCreated> message = Message.from(record, ProductCreated.class, jsonMapper);
        if (!ProductCreated.class.getSimpleName().equals(message.messageType())) {
            return;
        }

        Long productId = message.payload().productId();
        stockRegistrationService.registerIfAbsent(productId);
        stockRegistrationService.initializeAvailable(productId);
    }
}
