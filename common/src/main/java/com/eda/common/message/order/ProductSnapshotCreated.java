package com.eda.common.message.order;

public record ProductSnapshotCreated(Long productId, String name, long price) {
}
