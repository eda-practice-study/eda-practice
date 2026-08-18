package com.eda.product.adapter.out.messaging;

import com.eda.common.event.ProductCreated;
import com.eda.common.event.Topics;
import com.eda.product.application.port.out.PublishEventPort;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class EventPublisherAdapter implements PublishEventPort {

    private static final long SEND_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, ProductCreated> kafkaTemplate;

    @Override
    public void publish(ProductCreated event) {
        String key = String.valueOf(event.productId());

        try {
            SendResult<String, ProductCreated> result = kafkaTemplate
                    .send(Topics.PRODUCT_EVENTS, key, event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            RecordMetadata metadata = result.getRecordMetadata();
            log.info("ProductCreated 발행 성공 productId={}, topic={}, partition={}, offset={}",
                    event.productId(), metadata.topic(), metadata.partition(), metadata.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 발행 중 인터럽트 발생 productId=" + event.productId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka 발행 실패 productId=" + event.productId(), e);
        }
    }
}
