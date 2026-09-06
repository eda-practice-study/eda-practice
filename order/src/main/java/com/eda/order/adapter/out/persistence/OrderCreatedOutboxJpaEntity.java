package com.eda.order.adapter.out.persistence;

import com.eda.common.event.OrderCreatedEvent;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "order_created_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCreatedOutboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "order_created_outbox_line",
            joinColumns = @JoinColumn(name = "event_id")
    )
    @OrderColumn(name = "line_order")
    private List<OutboxLine> lines = new ArrayList<>();

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    private OrderCreatedOutboxJpaEntity(
            UUID eventId,
            Long orderId,
            List<OutboxLine> lines,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.lines = new ArrayList<>(lines);
        this.occurredAt = occurredAt;
        this.status = OutboxStatus.PENDING;
    }

    public static OrderCreatedOutboxJpaEntity create(
            OrderCreatedEvent event
    ) {
        List<OutboxLine> lines = event.lines().stream()
                .map(line -> new OutboxLine(
                        line.productId(),
                        line.quantity()
                ))
                .toList();

        return new OrderCreatedOutboxJpaEntity(
                event.eventId(),
                event.orderId(),
                lines,
                event.occurredAt()
        );
    }

    void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class OutboxLine {

        @Column(name = "product_id", nullable = false, updatable = false)
        private Long productId;

        @Column(nullable = false, updatable = false)
        private int quantity;

        private OutboxLine(Long productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }
    }
}