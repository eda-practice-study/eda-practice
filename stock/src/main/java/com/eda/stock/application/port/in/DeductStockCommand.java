package com.eda.stock.application.port.in;

import java.util.List;
import java.util.UUID;

public record DeductStockCommand(
        UUID eventId,
        Long orderId,
        List<LineCommand> lines
) {
    public record LineCommand(
        Long productId,
        int quantity
    ) {}
}
