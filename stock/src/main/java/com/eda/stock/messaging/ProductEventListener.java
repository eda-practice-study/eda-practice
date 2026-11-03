package com.eda.stock.messaging;

import com.eda.common.message.Message;
import com.eda.common.message.ProductCreated;
import com.eda.stock.application.StockService;
import com.eda.stock.domain.Stock;
import com.eda.stock.infrastructure.StockRedisRepository;
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
    private final StockRedisRepository stockRedisRepository;

    @KafkaListener(
            topics = "product.events",
            groupId = "stock-service"
    )
    public void onProductCreated(ConsumerRecord<String, String> record) {
        Message<ProductCreated> message =
                Message.from(record, ProductCreated.class, jsonMapper);

        ProductCreated event = message.payload();

        // stock db에 먼저 저장
        Stock stock = stockService.initializeStock(event.productId());

        // 이후 redis db에 캐싱
        stockRedisRepository.initializeIfAbsent(
                stock.getProductId(),
                stock.getQuantity()
        );


        log.info(
                "상품 초기 재 처리 완료: messageId={}, productId={}, name={}, price={}",
                message.messageId(),
                event.productId(),
                event.name(),
                event.price()
        );
    }
}
