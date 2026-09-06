package com.eda.order.domain.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderOutboxEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false)
    private AggregateType aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderOutboxStatus status;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    private OrderOutboxEvent(
            UUID eventId,
            AggregateType aggregateType,
            Long aggregateId,
            EventType eventType,
            String payload
    ) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OrderOutboxStatus.PENDING;
        this.occurredAt = LocalDateTime.now();
    }


    public static OrderOutboxEvent create(
            UUID eventId,
            AggregateType aggregateType,
            Long aggregateId,
            EventType eventType,
            String payload
    ) {
        return new OrderOutboxEvent(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                payload
        );
    }

    public void markPublished() {
        this.status = OrderOutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }
}