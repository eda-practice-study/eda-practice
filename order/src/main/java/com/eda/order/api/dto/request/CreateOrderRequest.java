package com.eda.order.api.dto.request;

import com.eda.order.application.OrderService.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

public record CreateOrderRequest(
        @NotEmpty
        @Size(max = 20)
        List<@NotNull @Valid Item> items
) {

    public record Item(
            @NotNull
            Long productId,

            @NotNull
            @Min(1)
            @Max(10)
            Integer quantity
    ) {
    }

    @AssertTrue(message = "productId 는 중복될 수 없습니다")
    public boolean isProductIdUnique() {
        if (items == null) {
            return true;
        }
        return items.stream().filter(Objects::nonNull).map(Item::productId).distinct().count() == items.size();
    }

    public List<OrderItem> toOrderItems() {
        return items.stream()
                .map(item -> new OrderItem(item.productId(), item.quantity()))
                .toList();
    }
}
