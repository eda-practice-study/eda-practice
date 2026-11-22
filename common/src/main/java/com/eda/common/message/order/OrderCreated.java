package com.eda.common.message.order;

import java.util.List;

public record OrderCreated(Long orderId, String orderKey, List<Item> items) {

    public record Item(Long productId, int quantity) {
    }
}
