package com.eda.stock.application.port.in;

import com.eda.stock.adapter.in.web.dto.AddStockRequest;

public interface AddStockUseCase {
    void add(AddStockRequest addStockRequest);
}
