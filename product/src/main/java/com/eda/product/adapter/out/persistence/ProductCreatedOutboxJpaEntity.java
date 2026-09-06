package com.eda.product.adapter.out.persistence;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_created_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCreatedOutboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal price;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    private ProductCreatedOutboxJpaEntity(
            UUID eventId,
            Long productId,
            String name,
            BigDecimal price,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.occurredAt = occurredAt;
        this.status = OutboxStatus.PENDING;
    }

    public static ProductCreatedOutboxJpaEntity create(
            UUID eventId,
            Long productId,
            String name,
            BigDecimal price,
            Instant occurredAt
    ) {
        return new ProductCreatedOutboxJpaEntity(
                eventId,
                productId,
                name,
                price,
                occurredAt
        );
    }

    void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

}
