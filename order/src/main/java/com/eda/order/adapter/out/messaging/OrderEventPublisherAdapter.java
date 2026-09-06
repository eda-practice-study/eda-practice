package com.eda.order.adapter.out.messaging;

import com.eda.common.event.OrderCreated;
import com.eda.common.event.Topics;
import com.eda.order.application.port.out.PublishOrderEventPort;
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
class OrderEventPublisherAdapter implements PublishOrderEventPort {

    private static final long SEND_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, OrderCreated> kafkaTemplate;

    @Override
    public void publish(OrderCreated event) {
        String key = String.valueOf(event.orderId());

        try {
            SendResult<String, OrderCreated> result = kafkaTemplate
                    .send(Topics.ORDER_EVENTS, key, event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            RecordMetadata metadata = result.getRecordMetadata();
            log.info("OrderCreated 발행 성공 orderId={}, topic={}, partition={}, offset={}",
                    event.orderId(), metadata.topic(), metadata.partition(), metadata.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 발행 중 인터럽트 발생 orderId=" + event.orderId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka 발행 실패 orderId=" + event.orderId(), e);
        }
    }
}
