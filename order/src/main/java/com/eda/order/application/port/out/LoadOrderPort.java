package com.eda.order.application.port.out;

import com.eda.order.domain.Order;
import java.util.Optional;

public interface LoadOrderPort {

    Optional<Order> findById(Long orderId);
}
