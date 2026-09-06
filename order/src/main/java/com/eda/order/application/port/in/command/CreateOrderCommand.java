package com.eda.order.application.port.in.command;

import java.util.List;

public record CreateOrderCommand(
        Long memberId,
        List<Line> lines
) {
    public record Line(
            Long productId,
            int quantity
    ) {
    }
}