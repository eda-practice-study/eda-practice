package com.eda.order.application.port.in.command;

import java.math.BigDecimal;

public record SynchronizeProductCatalogCommand(
        Long productId,
        String productName,
        BigDecimal unitPrice
) {
}
