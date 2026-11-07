package com.eda.order.event;

public record ProductSnapshotRegistered(Long productId, String name, long price) {
}
