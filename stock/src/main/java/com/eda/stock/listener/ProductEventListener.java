package com.eda.stock.listener;

import com.eda.common.message.Message;
import com.eda.common.message.MessageHeader;
import com.eda.common.message.product.ProductCreated;
import com.eda.stock.cache.StockCache;
import com.eda.stock.exception.InvalidProductEventException;
import com.eda.stock.service.StockService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final JsonMapper jsonMapper;
    private final StockService stockService;
    private final StockCache stockCache;

    @KafkaListener(id = "stock-product-events", topics = "product.events", groupId = "stock-service")
    public void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader(MessageHeader.MESSAGE_TYPE);
        if (type == null || type.value() == null) {
            throw new InvalidProductEventException("messageType 헤더가 필요합니다");
        }
        if (!ProductCreated.class.getSimpleName().equals(new String(type.value(), StandardCharsets.UTF_8))) {
            return;
        }

        Message<ProductCreated> message = getProductCreatedMessage(record);

        Long productId = message.payload().productId();
        boolean created = stockService.createFor(message.messageId(), productId);
        if (created) {
            stockCache.initialize(productId);
        }
    }

    private Message<ProductCreated> getProductCreatedMessage(ConsumerRecord<String, String> record) {
        Message<ProductCreated> message;
        try {
            message = Message.from(record, ProductCreated.class, jsonMapper);
            if (message.payload() == null) {
                throw new InvalidProductEventException("이벤트 본문이 필요합니다");
            }
        } catch (RuntimeException ex) {
            throw new InvalidProductEventException("상품 생성 이벤트 형식이 올바르지 않습니다", ex);
        }
        return message;
    }
}
