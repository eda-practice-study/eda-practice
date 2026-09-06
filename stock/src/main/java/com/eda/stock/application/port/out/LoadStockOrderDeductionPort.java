package com.eda.stock.application.port.out;

import com.eda.stock.domain.StockOrderDeduction;
import java.util.Optional;

public interface LoadStockOrderDeductionPort {

    Optional<StockOrderDeduction> findByOrderId(Long orderId);
}
