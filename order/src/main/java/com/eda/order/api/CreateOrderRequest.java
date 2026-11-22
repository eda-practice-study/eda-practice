package com.eda.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

// 메시지를 적어 두지 않으면 요청 언어(Accept-Language)나 JVM locale 에 따라 바뀐다
public record CreateOrderRequest(
        @NotEmpty(message = "상품은 1개 이상이어야 합니다")
        @Size(max = 20, message = "상품은 20개 이하여야 합니다")
        List<@Valid Item> items
) {

    public record Item(
            @NotNull(message = "상품 ID 는 필수입니다")
            Long productId,

            @NotNull(message = "수량은 필수입니다")
            @Min(value = 1, message = "1 이상이어야 합니다")
            @Max(value = 10, message = "10 이하여야 합니다")
            Integer quantity
    ) {
    }

    // stock 을 부르기 전에 막아야 하므로 도메인 검증까지 기다리지 않는다
    @AssertTrue(message = "같은 상품은 한 번만 담을 수 있습니다")
    public boolean isDistinctProducts() {
        return items == null || items.stream().map(Item::productId).distinct().count() == items.size();
    }
}
