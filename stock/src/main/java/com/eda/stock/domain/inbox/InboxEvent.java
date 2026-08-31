package com.eda.stock.domain.inbox;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "inbox_event",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inbox_event_type_aggregate",
                        columnNames = {"event_type", "aggregate_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxEvent {
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private String aggregateId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    private InboxEvent(UUID eventId, String eventType, String aggregateId, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.processedAt = processedAt;
    }

    public static InboxEvent processed(UUID eventId, String eventType, String aggregateId, LocalDateTime processedAt) {
        if(eventId == null || eventType == null || eventType.isBlank() || aggregateId == null || aggregateId.isBlank() || processedAt == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return new InboxEvent(eventId, eventType, aggregateId, processedAt);
    }
}
