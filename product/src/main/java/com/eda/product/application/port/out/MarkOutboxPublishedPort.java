package com.eda.product.application.port.out;

import java.util.UUID;

public interface MarkOutboxPublishedPort {

    void markPublished(UUID eventId);
}
