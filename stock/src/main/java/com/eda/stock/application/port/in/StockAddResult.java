package com.eda.stock.application.port.in;

/**
 * 재고 등록 결과
 *
 * @param addedQuantity 이번 요청으로 더한 수량
 * @param totalQuantity 더한 뒤의 총 재고
 */
public record StockAddResult(
        Long productId,
        int addedQuantity,
        int totalQuantity
) {
}
