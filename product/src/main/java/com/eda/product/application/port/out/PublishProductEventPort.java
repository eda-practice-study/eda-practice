package com.eda.product.application.port.out;

import com.eda.common.event.ProductCreatedEvent;

public interface PublishProductEventPort {

    void publishCreated(ProductCreatedEvent event);
}
