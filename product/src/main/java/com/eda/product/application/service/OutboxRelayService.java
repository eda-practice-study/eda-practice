package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.in.RelayProductEventUseCase;
import com.eda.product.application.port.out.LoadPendingOutboxPort;
import com.eda.product.application.port.out.MarkOutboxPublishedPort;
import com.eda.product.application.port.out.PublishProductEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxRelayService implements RelayProductEventUseCase {

    private final LoadPendingOutboxPort loadPendingOutboxPort;
    private final PublishProductEventPort publishProductEventPort;
    private final MarkOutboxPublishedPort markOutboxPublishedPort;

    @Transactional
    @Override
    public void relay() {
        List<ProductCreatedEvent> events =
                loadPendingOutboxPort.loadPending();

        for (ProductCreatedEvent event : events) {
            publishProductEventPort.publish(event);
            markOutboxPublishedPort.markPublished(event.eventId());
        }

    }
}
