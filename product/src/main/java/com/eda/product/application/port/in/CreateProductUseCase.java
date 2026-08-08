package com.eda.product.application.port.in;

import java.math.BigDecimal;

public interface CreateProductUseCase {

    Long create(String productName, BigDecimal price);
}
