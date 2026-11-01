package com.eda.common.message.order;

import java.math.BigDecimal;

public record ProductSnapshotRegistered(Long productId, String name, BigDecimal price) {
}
