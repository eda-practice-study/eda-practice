package com.eda.product.application.port.out;

import com.eda.common.event.ProductCreatedEvent;

public interface PublishProductCreatedEventPort {
    void publish(ProductCreatedEvent event);
}
