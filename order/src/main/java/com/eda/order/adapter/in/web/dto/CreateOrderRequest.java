package com.eda.order.adapter.in.web.dto;

import com.eda.order.application.port.in.command.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty
        List<@Valid Line> lines
) {
    public record Line(
            @NotNull
            @Positive
            Long productId,

            @Positive
            int quantity
    ) {
    }

    public CreateOrderCommand toCommand(Long memberId) {
        return new CreateOrderCommand(
                memberId,
                lines.stream()
                        .map(line -> new CreateOrderCommand.Line(
                                line.productId(),
                                line.quantity()
                        ))
                        .toList()
        );
    }
}
