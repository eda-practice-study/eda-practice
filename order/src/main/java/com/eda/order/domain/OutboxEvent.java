package com.eda.order.domain;

import com.eda.common.domain.BaseEntity;
import com.eda.common.event.AggregateType;
import com.eda.common.event.EventTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outbox_event", indexes = @Index(name = "idx_order_outbox_event_published", columnList = "published"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50, comment = "애그리거트 타입")
    private AggregateType aggregateType;

    @Column(nullable = false, comment = "애그리거트 ID")
    private Long aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100, comment = "이벤트 타입")
    private EventTypes eventType;

    @Column(nullable = false, columnDefinition = "text", comment = "이벤트 페이로드(JSON)")
    private String payload;

    @Column(nullable = false, comment = "발행 여부")
    private boolean published;

    @Column(comment = "발행 시각")
    private LocalDateTime publishedAt;

    private OutboxEvent(AggregateType aggregateType, Long aggregateId, EventTypes eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.published = false;
    }

    public static OutboxEvent create(
            AggregateType aggregateType,
            Long aggregateId,
            EventTypes eventType,
            String payload
    ) {
        return new OutboxEvent(aggregateType, aggregateId, eventType, payload);
    }

    public void markPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }
}
