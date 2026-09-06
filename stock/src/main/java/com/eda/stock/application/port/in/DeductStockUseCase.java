package com.eda.stock.application.port.in;

import com.eda.common.event.OrderCreated;

public interface DeductStockUseCase {

    void deduct(OrderCreated event);
}
