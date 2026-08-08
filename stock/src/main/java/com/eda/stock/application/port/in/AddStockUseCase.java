package com.eda.stock.application.port.in;

public interface AddStockUseCase {

    StockAddResult add(Long productId, int quantity);
}
