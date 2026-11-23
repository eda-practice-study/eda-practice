package com.eda.order.api.dto.response;

import com.eda.order.domain.Order;
import com.eda.order.domain.OrderLine;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CreateOrderResponse(
        Long orderId,
        String status,
        BigDecimal totalAmount,
        List<Item> items,
        LocalDateTime createdAt
) {

    public static CreateOrderResponse from(Order order) {
        return new CreateOrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getOrderLines().stream().map(Item::from).toList(),
                order.getCreatedAt());
    }

    // productName · unitPrice 는 주문 시점의 상품 스냅샷 값
    public record Item(Long productId, String productName, BigDecimal unitPrice, int quantity) {

        static Item from(OrderLine line) {
            return new Item(line.getProductId(), line.getProductName(), line.getUnitPrice(), line.getQuantity());
        }
    }
}
