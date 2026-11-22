package com.eda.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock_decrease_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockDecreaseLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, comment = "상품 ID")
    private Long productId;

    @Column(nullable = false, comment = "차감 수량")
    private int quantity;

    private StockDecreaseLine(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public static StockDecreaseLine of(Long productId, int quantity) {
        return new StockDecreaseLine(productId, quantity);
    }
}
