package com.eda.order.adapter.in.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record OrderCreateRequest(

        @NotEmpty(message = "주문 상품은 하나 이상이어야 합니다.")
        List<@Valid OrderLineRequest> lines
) {
}
