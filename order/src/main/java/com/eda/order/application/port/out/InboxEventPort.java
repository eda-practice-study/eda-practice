package com.eda.order.application.port.out;

import java.util.UUID;

public interface InboxEventPort {
    boolean existsByEventId(UUID eventId);

    void save(UUID eventId, String eventType);
}
