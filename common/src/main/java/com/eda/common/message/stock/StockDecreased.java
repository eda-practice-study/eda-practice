package com.eda.common.message.stock;

import java.util.List;

// 주문 생성 이벤트를 받아 Stock DB 재고를 차감했음. quantity = 차감 후 DB 실재고
public record StockDecreased(Long orderId, List<Item> items) {

    public record Item(Long productId, int decreasedQuantity, int quantity) {
    }
}
