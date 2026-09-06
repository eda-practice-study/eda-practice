package com.eda.order.application.port.out;

import java.util.UUID;

public interface MarkOrderCreatedOutboxPublishedPort {

    void markPublished(UUID eventId);
}
