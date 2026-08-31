package com.eda.order.application.port.in;

import java.util.List;

public record CreateOrderCommand(
        Long memberId,
        List<LineCommand> lines
) {
    public record LineCommand(
            Long productId,
            int quantity
    ) {

    }
}
