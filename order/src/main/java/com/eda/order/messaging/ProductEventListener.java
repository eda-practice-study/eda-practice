package com.eda.order.messaging;

import com.eda.common.message.Message;
import com.eda.common.message.ProductCreated;
import com.eda.order.application.ProductSnapShotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final JsonMapper jsonMapper;
    private final ProductSnapShotService productSnapShotService;

    @KafkaListener(
            topics = "product.events",
            groupId = "order-service"
    )
    public void onProductCreated(ConsumerRecord<String, String> record) {
        Message<ProductCreated> message =
                Message.from(record, ProductCreated.class, jsonMapper);

        ProductCreated event = message.payload();

        productSnapShotService.register(
                event.productId(),
                event.name(),
                BigDecimal.valueOf(event.price())
        );

        log.info(
                "상품 스냅샷 처리완료: messageId={}, productId={}",
                message.messageId(),
                event.productId()
        );
    }

}
