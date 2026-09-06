package com.eda.product.application.port.out;

import com.eda.common.event.ProductCreatedEvent;

import java.util.List;

public interface LoadPendingOutboxPort {

    List<ProductCreatedEvent> loadPending();
}
