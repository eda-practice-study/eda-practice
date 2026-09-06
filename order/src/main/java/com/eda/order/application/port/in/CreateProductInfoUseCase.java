package com.eda.order.application.port.in;

import java.math.BigDecimal;

public interface CreateProductInfoUseCase {

    void createFor(Long productId, String productName, BigDecimal price);
}
