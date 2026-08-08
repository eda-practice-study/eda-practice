package com.eda.product.application.port.out;

import com.eda.common.event.ProductCreated;

public interface PublishEventPort {

    void publish(ProductCreated event);
}
