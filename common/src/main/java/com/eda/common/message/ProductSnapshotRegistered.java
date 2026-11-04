package com.eda.common.message;

public record ProductSnapshotRegistered(
        Long productId,
        String name,
        long price
) {
}
