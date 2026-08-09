package com.eda.product.application.port.in;

import java.math.BigDecimal;

public interface RegisterProductUseCase {

    Long register(RegisterProductCommand command);

    record RegisterProductCommand(String name, BigDecimal price) {
    }
}
