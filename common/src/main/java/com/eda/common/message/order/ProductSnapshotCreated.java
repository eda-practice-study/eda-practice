package com.eda.common.message.order;

import java.math.BigDecimal;

public record ProductSnapshotCreated(
        Long productSnapshotId,
        Long productId,
        String name,
        BigDecimal price
) {
}
