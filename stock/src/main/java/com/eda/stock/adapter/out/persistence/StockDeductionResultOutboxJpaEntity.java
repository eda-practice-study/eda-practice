package com.eda.stock.adapter.out.persistence;


import com.eda.common.event.StockDeductionResultEvent;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_deduction_result_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockDeductionResultOutboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deduction_result", nullable = false, updatable = false)
    private StockDeductionResultEvent.Result result;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    private StockDeductionResultOutboxJpaEntity(
            UUID eventId,
            Long orderId,
            StockDeductionResultEvent.Result result,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.result = result;
        this.occurredAt = occurredAt;
        this.status = OutboxStatus.PENDING;
    }

    public static StockDeductionResultOutboxJpaEntity create(
            StockDeductionResultEvent event
    ) {
        return new StockDeductionResultOutboxJpaEntity(
                event.eventId(),
                event.orderId(),
                event.result(),
                event.occurredAt()
        );
    }

    void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }
}
