package com.eda.common.event;

import java.math.BigDecimal;

public record ProductCreated(
        Long productId,
        String productName,
        BigDecimal price
) {

}
