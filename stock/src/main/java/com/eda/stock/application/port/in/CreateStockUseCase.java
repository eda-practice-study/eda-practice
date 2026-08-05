package com.eda.stock.application.port.in;

import com.eda.stock.domain.Stock;

public interface CreateStockUseCase {
    Stock create(CreateStockCommand command);
}
