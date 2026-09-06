package com.eda.order.application.port.out;

import com.eda.order.domain.ProductInfo;

public interface SaveProductInfoPort {

    ProductInfo save(ProductInfo productInfo);
}
