package com.eda.product.adapter.out.messaging;

import com.eda.common.event.ProductCreated;
import com.eda.common.event.Topics;
import com.eda.product.application.port.out.PublishEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class EventPublisherAdapter implements PublishEventPort {

    private final KafkaTemplate<String, ProductCreated> kafkaTemplate;

    @Override
    public void publish(ProductCreated event) {
        String key = String.valueOf(event.productId());

        kafkaTemplate.send(Topics.PRODUCT_EVENTS, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("ProductCreated 발행 실패 productId={}, topic={}", event.productId(), Topics.PRODUCT_EVENTS, ex);
                        return;
                    }

                    var metadata = result.getRecordMetadata();
                    log.info("ProductCreated 발행 성공 productId={}, topic={}, partition={}, offset={}",
                            event.productId(), metadata.topic(), metadata.partition(), metadata.offset());
                });
    }
}
