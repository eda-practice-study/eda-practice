package com.eda.order.application.port.out;

import com.eda.order.domain.Order;

public interface SaveOrderPort {
    Order save(Order order);
}
