package com.eda.stock.adapter.out.kafka;

import com.eda.common.event.StockDeductionResultEvent;
import com.eda.stock.application.port.out.PublishStockResultEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockResultEventKafkaAdapter
    implements PublishStockResultEventPort {

    private static final String TOPIC = "stock.events";

    private final KafkaTemplate<String, StockDeductionResultEvent> kafkaTemplate;

    @Override
    public void publish(StockDeductionResultEvent event) {
        String key = event.orderId().toString();

        kafkaTemplate.send(
                TOPIC,
                key,
                event
        ).join();
    }
}
