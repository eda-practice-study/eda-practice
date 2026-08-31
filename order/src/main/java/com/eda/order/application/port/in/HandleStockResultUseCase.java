package com.eda.order.application.port.in;

public interface HandleStockResultUseCase {
    void handleDeducted(Long orderId);
    void handleDeductionFailed(Long orderId);
}
