package com.eda.stock.application.port.out;

import com.eda.stock.domain.OutboxEvent;
import java.util.List;
import java.util.Optional;

public interface LoadStockOutboxEventPort {

    List<Long> findUnpublishedIds();

    Optional<OutboxEvent> findByIdForUpdate(Long id);
}
