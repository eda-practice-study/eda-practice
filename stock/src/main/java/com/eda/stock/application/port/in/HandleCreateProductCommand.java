package com.eda.stock.application.port.in;

import java.util.UUID;

public record HandleCreateProductCommand(
        UUID eventId,
        Long productId
) {
}
