package com.eda.product.adapter.out.messaging;

import com.eda.common.event.EventTopics;
import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.out.PublishProductEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventPublisherAdapter implements PublishProductEventPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishCreated(ProductCreatedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
            return;
        }

        send(event);
    }

    private void send(ProductCreatedEvent event) {
        kafkaTemplate.send(EventTopics.PRODUCT_EVENTS, String.valueOf(event.productId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("상품 생성 이벤트 발행 실패 - 재고가 생성되지 않습니다. eventId={}, productId={}",
                                event.eventId(), event.productId(), ex);
                        return;
                    }
                    log.info("상품 생성 이벤트 발행 완료. eventId={}, productId={}", event.eventId(), event.productId());
                });
    }
}
