package com.eda.order.domain;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "product_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, comment = "상품 ID")
    private Long productId;

    @Column(nullable = false, comment = "상품명")
    private String name;

    @Column(nullable = false, precision = 19, scale = 0, comment = "상품 가격")
    private BigDecimal price;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, comment = "생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, comment = "수정 시각")
    private LocalDateTime updatedAt;

    private ProductSnapshot(Long productId, String name, BigDecimal price) {
        this.productId = productId;
        this.name = name;
        this.price = price;
    }

    public static ProductSnapshot of(Long productId, String name, BigDecimal price) {
        if (productId == null || name == null || name.isBlank() || price == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new ProductSnapshot(productId, name, price);
    }

    public Order.LineItem toLineItem(int quantity) {
        return new Order.LineItem(productId, name, price, quantity);
    }
}
