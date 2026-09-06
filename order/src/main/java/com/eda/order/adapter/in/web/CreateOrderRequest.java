package com.eda.order.adapter.in.web;

import com.eda.order.application.port.in.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(

        @NotEmpty(message = "주문 항목은 하나 이상이어야 합니다.")
        List<@Valid Line> lines

) {
    public record Line (
            @NotNull(message = "상품ID는 필수입니다.")
            Long productId,

            @Positive(message = "주문 수량은 0보다 커야 합니다.")
            int quantity
    ) {}

    public CreateOrderCommand toCommand(Long memberId) {
        List<CreateOrderCommand.Line> commandLines =
                lines.stream()
                        .map(line -> new CreateOrderCommand.Line(
                                line.productId,
                                line.quantity
                        ))
                        .toList();
        return new CreateOrderCommand(
                memberId,
                commandLines
        );
    }
}
