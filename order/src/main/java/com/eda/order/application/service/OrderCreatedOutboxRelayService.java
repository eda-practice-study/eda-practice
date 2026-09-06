package com.eda.order.application.service;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.order.application.port.in.RelayOrderCreatedEventUseCase;
import com.eda.order.application.port.out.LoadPendingOrderCreatedOutboxPort;
import com.eda.order.application.port.out.MarkOrderCreatedOutboxPublishedPort;
import com.eda.order.application.port.out.PublishOrderCreatedEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderCreatedOutboxRelayService implements RelayOrderCreatedEventUseCase {

    private final LoadPendingOrderCreatedOutboxPort loadPendingOrderCreatedOutboxPort;
    private final PublishOrderCreatedEventPort publishOrderCreatedEventPort;
    private final MarkOrderCreatedOutboxPublishedPort markOrderCreatedOutboxPublishedPort;

    @Override
    @Transactional
    public void relay() {
        List<OrderCreatedEvent> events = loadPendingOrderCreatedOutboxPort.loadPending();

        for (OrderCreatedEvent event : events) {
            publishOrderCreatedEventPort.publish(event);

            markOrderCreatedOutboxPublishedPort.markPublished(event.eventId());
        }
    }
}
