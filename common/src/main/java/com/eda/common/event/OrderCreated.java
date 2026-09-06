package com.eda.common.event;

import java.util.List;

public record OrderCreated(
        String eventId,
        Long orderId,
        List<Line> lines
) {

    public record Line(Long productId, int quantity) {
    }
}
