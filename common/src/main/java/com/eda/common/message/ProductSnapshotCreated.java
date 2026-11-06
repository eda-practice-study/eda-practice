package com.eda.common.message;

import java.math.BigDecimal;

public record ProductSnapshotCreated(
        Long productSnapshotId,
        Long productId,
        String name,
        BigDecimal price
) {
}
