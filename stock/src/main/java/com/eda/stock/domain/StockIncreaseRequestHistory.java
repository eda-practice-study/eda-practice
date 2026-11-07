package com.eda.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "stock_increase_request", uniqueConstraints =
        @UniqueConstraint(name = "uk_stock_increase_key", columnNames = "idempotency_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockIncreaseRequestHistory {

    public static final int MAX_KEY_LENGTH = 128;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = MAX_KEY_LENGTH)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Long productId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public StockIncreaseRequestHistory(String idempotencyKey, Long productId) {
        this.idempotencyKey = idempotencyKey;
        this.productId = productId;
    }
}
