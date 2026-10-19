package com.eda.product.domain;

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
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    public static final int MAX_NAME_LENGTH = 100;
    public static final BigDecimal MAX_PRICE = BigDecimal.valueOf(1_000_000);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = MAX_NAME_LENGTH, comment = "상품명")
    private String name;

    @Column(nullable = false, precision = 19, scale = 0, comment = "상품 가격")
    private BigDecimal price;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, comment = "생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, comment = "수정 시각")
    private LocalDateTime updatedAt;

    private Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }

    public static Product register(String name, BigDecimal price) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (!isValidPrice(price)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new Product(name, price);
    }

    private static boolean isValidPrice(BigDecimal price) {
        if (price == null) {
            return false;
        }

        boolean integer = price.stripTrailingZeros().scale() <= 0;
        return integer && price.compareTo(BigDecimal.ONE) >= 0 && price.compareTo(MAX_PRICE) <= 0;
    }
}
