package com.eda.order.api;

import com.eda.order.domain.Order;
import com.eda.order.domain.OrderLine;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String status,
        long totalAmount,
        List<Item> items,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt
) {

    public record Item(Long productId, String productName, long unitPrice, int quantity) {

        static Item from(OrderLine line) {
            return new Item(line.getProductId(), line.getProductName(), line.getUnitPrice().longValueExact(),
                    line.getQuantity());
        }
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getStatus().name(), order.getTotalAmount().longValueExact(),
                order.getOrderLines().stream().map(Item::from).toList(), order.getCreatedAt());
    }
}
