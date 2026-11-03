package com.eda.stock.messaging;

import com.eda.common.message.Message;
import com.eda.common.message.ProductCreated;
import com.eda.stock.application.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final JsonMapper jsonMapper;
    private final StockService stockService;

    @KafkaListener(
            topics = "product.events",
            groupId = "stock-service"
    )
    public void onProductCreated(ConsumerRecord<String, String> record) {
        Message<ProductCreated> message =
                Message.from(record, ProductCreated.class, jsonMapper);

        ProductCreated event = message.payload();

        stockService.initializeStock(event.productId());

        log.info(
                "상품 초기 재 처리 완료: messageId={}, productId={}, name={}, price={}",
                message.messageId(),
                event.productId(),
                event.name(),
                event.price()
        );
    }
}
