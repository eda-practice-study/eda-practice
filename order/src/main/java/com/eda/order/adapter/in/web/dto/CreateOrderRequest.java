package com.eda.order.adapter.in.web.dto;

import com.eda.order.application.port.in.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty
        List<@Valid LineRequest> lines
) {
    public CreateOrderCommand toCommand(Long memberId) {
        List<CreateOrderCommand.LineCommand> commands = lines.stream()
                .map(line -> new CreateOrderCommand.LineCommand(
                        line.productId(),
                        line.quantity()
                ))
                .toList();

        return new CreateOrderCommand(memberId, commands);
    }

    public record LineRequest(
            @NotNull
            @Positive
            Long productId,

            @NotNull
            @Positive
            Integer quantity
    ) {
    }
}
