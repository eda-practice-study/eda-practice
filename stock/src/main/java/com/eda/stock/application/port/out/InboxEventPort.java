package com.eda.stock.application.port.out;

import java.util.UUID;

public interface InboxEventPort {

    boolean existByEventId(UUID eventId);

    void save(UUID eventId, String eventType);
}
