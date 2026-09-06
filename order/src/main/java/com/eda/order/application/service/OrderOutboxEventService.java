package com.eda.order.application.service;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.order.application.port.in.PublishPendingOrderOutboxEventUseCase;
import com.eda.order.application.port.out.LoadPendingOrderOutboxEventPort;
import com.eda.order.application.port.out.PublishOrderEventPort;
import com.eda.order.domain.outbox.OrderOutboxEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderOutboxEventService
        implements PublishPendingOrderOutboxEventUseCase {

    private final LoadPendingOrderOutboxEventPort loadPort;
    private final PublishOrderEventPort publishPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishPendingEvents() {
        List<OrderOutboxEvent> events = loadPort.loadPendingEvents();

        for (OrderOutboxEvent outboxEvent : events) {
            OrderCreatedEvent event = objectMapper.readValue(
                    outboxEvent.getPayload(),
                    OrderCreatedEvent.class
            );

            publishPort.publish(event);
            outboxEvent.markPublished();
        }
    }
}