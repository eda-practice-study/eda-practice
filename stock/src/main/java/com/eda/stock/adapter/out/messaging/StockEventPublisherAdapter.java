package com.eda.stock.adapter.out.messaging;

import com.eda.common.event.StockDeductedEvent;
import com.eda.common.event.StockDeductionFailedEvent;
import com.eda.stock.application.port.out.PublishStockEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventPublisherAdapter implements PublishStockEventPort {

    private static final String STOCK_EVENTS_TOPIC = "stock.events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publish(StockDeductedEvent event) {
        kafkaTemplate.send(STOCK_EVENTS_TOPIC, event.orderId().toString(), event).join();
    }

    @Override
    public void publish(StockDeductionFailedEvent event) {
        kafkaTemplate.send(STOCK_EVENTS_TOPIC, event.orderId().toString(), event).join();
    }
}
