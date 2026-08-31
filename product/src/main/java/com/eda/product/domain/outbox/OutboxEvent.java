package com.eda.product.domain.outbox;

import com.eda.common.domain.BaseEntity;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {
    @NotBlank
    @Column(nullable = false)
    private String aggregateType;

    @NotBlank
    @Column(nullable = false)
    private String aggregateId;

    @NotBlank
    @Column(nullable = false)
    private String eventType;

    @NotBlank
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    private LocalDateTime publishedAt;

    private OutboxEvent (
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload
    ) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxEvent pending(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payload
    ) {
        if (aggregateType == null || aggregateType.isBlank()
                || aggregateId == null || aggregateId.isBlank()
                || eventType == null || eventType.isBlank()
                || payload == null || payload.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return new OutboxEvent(aggregateType, aggregateId, eventType, payload);
    }

    public void markPublished(LocalDateTime publishedAt) {
        if (publishedAt == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if(this.status == OutboxStatus.PENDING) {
            this.status = OutboxStatus.PUBLISHED;
            this.publishedAt = publishedAt;
        }
    }
}
