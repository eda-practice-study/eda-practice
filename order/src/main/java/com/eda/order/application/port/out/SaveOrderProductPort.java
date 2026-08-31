package com.eda.order.application.port.out;

import com.eda.order.domain.OrderProduct;

public interface SaveOrderProductPort {
    OrderProduct save(OrderProduct orderProduct);
}
