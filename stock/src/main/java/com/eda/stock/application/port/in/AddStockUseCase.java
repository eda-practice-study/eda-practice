package com.eda.stock.application.port.in;

import com.eda.stock.domain.Stock;

public interface AddStockUseCase {
    Stock add(AddStockCommand command);
}
