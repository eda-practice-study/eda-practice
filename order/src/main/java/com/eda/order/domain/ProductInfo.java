package com.eda.order.domain;

import com.eda.common.domain.BaseEntity;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * order 서비스가 product.events로 동기화해 두는 상품 조회 모델이다.
 * 주문 생성 시 OrderLine에 다시 복사되므로 상품 정보 변경이 기존 주문에 영향을 주지 않는다.
 */
@Entity
@Table(name = "product_info", uniqueConstraints = @UniqueConstraint(columnNames = "product_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductInfo extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true, comment = "상품 ID")
    private Long productId;

    @Column(nullable = false, comment = "상품명")
    private String name;

    @Column(nullable = false, precision = 19, scale = 2, comment = "상품 가격")
    private BigDecimal price;

    private ProductInfo(Long productId, String name, BigDecimal price) {
        this.productId = productId;
        this.name = name;
        this.price = price;
    }

    public static ProductInfo create(Long productId, String name, BigDecimal price) {
        if (productId == null || name == null || name.isBlank() || price == null
                || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return new ProductInfo(productId, name, price);
    }
}
