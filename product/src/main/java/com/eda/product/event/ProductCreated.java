package com.eda.product.event;

public record ProductCreated(Long productId, String name, long price) {
}
