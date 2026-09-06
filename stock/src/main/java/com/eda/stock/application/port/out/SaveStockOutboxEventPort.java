package com.eda.stock.application.port.out;

import com.eda.stock.domain.OutboxEvent;

public interface SaveStockOutboxEventPort {

    OutboxEvent save(OutboxEvent outboxEvent);
}
