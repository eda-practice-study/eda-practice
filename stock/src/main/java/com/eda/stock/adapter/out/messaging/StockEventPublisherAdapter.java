package com.eda.stock.adapter.out.messaging;

import com.eda.common.event.StockDeductionResult;
import com.eda.common.event.Topics;
import com.eda.stock.application.port.out.PublishStockEventPort;
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
class StockEventPublisherAdapter implements PublishStockEventPort {

    private static final long SEND_TIMEOUT_SECONDS = 3;

    private final KafkaTemplate<String, StockDeductionResult> kafkaTemplate;

    @Override
    public void publish(StockDeductionResult event) {
        try {
            SendResult<String, StockDeductionResult> result = kafkaTemplate
                    .send(Topics.STOCK_EVENTS, String.valueOf(event.orderId()), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            RecordMetadata metadata = result.getRecordMetadata();
            log.info("재고 차감 결과 발행 성공 orderId={}, topic={}, partition={}, offset={}",
                    event.orderId(), metadata.topic(), metadata.partition(), metadata.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 발행 중 인터럽트 발생 orderId=" + event.orderId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka 발행 실패 orderId=" + event.orderId(), e);
        }
    }
}
