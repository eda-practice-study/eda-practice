package com.eda.common.message.stock;

import java.util.List;

// quantity 는 차감 직후의 실재고다. 키가 productId 인 StockIncreased 와는 순서가 보장되지 않으므로 재고 현황으로 쓰지 않는다.
public record StockDecreased(Long orderId, List<Item> items) {

    public record Item(Long productId, int decreasedQuantity, int quantity) {
    }
}
