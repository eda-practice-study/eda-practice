package com.eda.order.application.port.out;

import com.eda.order.domain.ProductInfo;

import java.util.List;

public interface LoadProductInfoPort {

    List<ProductInfo> loadAllByProductIds(List<Long> productIds);
}
