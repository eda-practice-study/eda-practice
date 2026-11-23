package com.eda.stock.event;

import com.eda.common.message.Message;
import com.eda.common.message.Topics;
import com.eda.common.message.product.ProductCreated;
import com.eda.stock.application.StockService;
import com.eda.stock.domain.Stock;
import com.eda.stock.infrastructure.StockRedisRepository;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final StockService stockService;
    private final StockRedisRepository stockRedisRepository;

    @KafkaListener(topics = Topics.PRODUCT_EVENTS, groupId = "stock-service")
    public void handleProductCreated(ConsumerRecord<String, String> record) {
        if (!Message.isType(record, ProductCreated.class)) {
            return;
        }

        ProductCreated event = Message.from(record, ProductCreated.class).payload();
        Stock stock = stockService.register(event.productId());
        stockRedisRepository.initialize(stock.getProductId(), stock.getQuantity());
    }
}
