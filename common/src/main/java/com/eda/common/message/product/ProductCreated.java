package com.eda.common.message.product;

public record ProductCreated(Long productId, String name, long price) {
}
