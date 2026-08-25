package com.eda.order.application.port.out;

import com.eda.order.domain.OrderProduct;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FindOrderProductPort {
    Optional<OrderProduct> findByProductId(Long productId);
    List<OrderProduct> findAllByProductIdIn(Collection<Long> productIds);
}
