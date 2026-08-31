package com.eda.stock.application.port.in;


public interface DeductStockUseCase {
    void deduct(DeductStockCommand command);
}
