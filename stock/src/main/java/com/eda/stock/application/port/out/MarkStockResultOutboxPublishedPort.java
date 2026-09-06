package com.eda.stock.application.port.out;

import java.util.UUID;

public interface MarkStockResultOutboxPublishedPort {

    void markPublished(UUID eventId);
}
