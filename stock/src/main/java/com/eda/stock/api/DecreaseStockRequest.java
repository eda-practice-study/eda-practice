package com.eda.stock.api;

import com.eda.stock.application.AvailableStockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record DecreaseStockRequest(
        @NotNull(message = "주문 키는 필수입니다")
        UUID orderKey,

        @NotEmpty(message = "상품은 1개 이상이어야 합니다")
        @Size(max = 20, message = "상품은 20개 이하여야 합니다")
        List<@Valid Item> items
) {

    public record Item(
            @NotNull(message = "상품 ID 는 필수입니다")
            Long productId,

            @NotNull(message = "수량은 필수입니다")
            @Min(value = 1, message = "1 이상이어야 합니다")
            Integer quantity
    ) {
    }

    public List<AvailableStockService.Item> toItems() {
        return items.stream().map(item -> new AvailableStockService.Item(item.productId(), item.quantity())).toList();
    }
}
