package com.eda.common.message.order;

public record ProductSnapshotRegistered(Long productId, String name, long price) {
}
