package com.eda.order.adapter.out.persistence;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEventJpaEntity {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    private InboxEventJpaEntity(
            UUID eventId,
            String eventType,
            Instant processedAt
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public static InboxEventJpaEntity create(
            UUID eventId,
            String eventType
    ) {
        return new InboxEventJpaEntity(
                eventId,
                eventType,
                Instant.now()
        );
    }
}
