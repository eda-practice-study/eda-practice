package com.eda.product.application.port.out;

import com.eda.product.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;

public interface LoadOutboxEventPort {

    List<Long> findUnpublishedIds();

    Optional<OutboxEvent> findByIdForUpdate(Long id);
}
