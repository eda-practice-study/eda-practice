package com.eda.order.application.port.out;

import com.eda.order.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;

public interface LoadOrderOutboxEventPort {

    List<Long> findUnpublishedIds();

    Optional<OutboxEvent> findByIdForUpdate(Long id);
}
