package com.eda.common.event;

public record ProductCreated(Long productId, String name, long price) {
}
