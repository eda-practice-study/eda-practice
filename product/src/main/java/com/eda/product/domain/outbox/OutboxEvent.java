package com.eda.product.domain.outbox;

import com.eda.common.domain.BaseEntity;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 발행할 이벤트를 도메인 변경과 같은 트랜잭션에 기록해두는 레코드.
 * 릴레이가 PENDING 을 골라 Kafka 로 보낸 뒤 PUBLISHED 로 바꾼다.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false, length = 50, comment = "애그리거트 종류")
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100, comment = "애그리거트 ID (Kafka 메시지 키로 사용)")
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100, comment = "이벤트 종류")
    private String eventType;

    @Column(nullable = false, columnDefinition = "text", comment = "이벤트 JSON 원문")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, comment = "발행 상태")
    private OutboxStatus status;

    @Column(name = "published_at", comment = "발행 시각. 미발행이면 null")
    private LocalDateTime publishedAt;

    private OutboxEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxEvent pending(String aggregateType, String aggregateId, String eventType, String payload) {
        requireNotBlank(aggregateType);
        requireNotBlank(aggregateId);
        requireNotBlank(eventType);
        requireNotBlank(payload);

        return new OutboxEvent(aggregateType, aggregateId, eventType, payload);
    }

    /**
     * 발행 완료 표시. 같은 레코드를 두 번 발행해도 최초 발행 시각을 덮어쓰지 않는다.
     */
    public void markPublished(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (this.status == OutboxStatus.PUBLISHED) {
            return;
        }

        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    private static void requireNotBlank(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
