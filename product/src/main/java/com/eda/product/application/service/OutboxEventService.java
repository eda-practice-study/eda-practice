package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.in.PublishPendingOutboxEventUseCase;
import com.eda.product.application.port.out.LoadPendingOutboxEventPort;
import com.eda.product.application.port.out.PublishProductEventPort;
import com.eda.product.application.port.out.SaveOutboxEventPort;
import com.eda.product.domain.outbox.OutboxEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxEventService implements PublishPendingOutboxEventUseCase {

    private final LoadPendingOutboxEventPort loadPendingOutboxEventPort;
    private final PublishProductEventPort publishProductEventPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishPendingEvents() {

        List<OutboxEvent> outboxEvents = loadPendingOutboxEventPort.loadPendingEvents();

        for (OutboxEvent outboxEvent : outboxEvents) {
            ProductCreatedEvent event =
                    objectMapper.readValue(
                            outboxEvent.getPayload(),
                            ProductCreatedEvent.class
                    );

            publishProductEventPort.publish(event);

            outboxEvent.markPublished();
        }
    }
}
