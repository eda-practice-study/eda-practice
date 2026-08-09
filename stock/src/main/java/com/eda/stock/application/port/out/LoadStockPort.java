package com.eda.stock.application.port.out;

public interface LoadStockPort {

    boolean existsByProductId(Long productId);
}
