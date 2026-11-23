package com.eda.stock.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

// reservationId = 호출하는 쪽이 만든 멱등 키. 재시도할 때도 같은 값을 보낸다
public record ReserveStockRequest(
        @NotNull
        UUID reservationId,

        @NotEmpty
        @Size(max = 20)
        List<@NotNull @Valid Item> items
) {

    public record Item(
            @NotNull
            Long productId,

            @NotNull
            @Min(1)
            @Max(10_000)
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

    // 요청 순서를 유지한다
    public Map<Long, Integer> toQuantities() {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        items.forEach(item -> quantities.put(item.productId(), item.quantity()));
        return quantities;
    }
}
