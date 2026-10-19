package com.eda.common.outbox;

import com.eda.common.message.Message;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "outbox_message", indexes = @Index(name = "idx_outbox_status_id", columnList = "status, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, comment = "메시지 ID (멱등 키)")
    private UUID messageId;

    @Column(nullable = false, comment = "토픽")
    private String topic;

    @Column(nullable = false, comment = "파티션 키")
    private String messageKey;

    @Column(nullable = false, comment = "메시지 타입")
    private String messageType;

    @Column(nullable = false, comment = "발생 시각")
    private Instant occurredAt;

    @Column(nullable = false, columnDefinition = "text", comment = "본문 (JSON)")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, comment = "발행 상태")
    private OutboxStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, comment = "생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, comment = "수정 시각")
    private LocalDateTime updatedAt;

    @Column(comment = "발행 시각")
    private LocalDateTime publishedAt;

    private OutboxMessage(String topic, String messageKey, Message<?> message, String payload) {
        this.messageId = message.messageId();
        this.topic = topic;
        this.messageKey = messageKey;
        this.messageType = message.messageType();
        this.occurredAt = message.occurredAt();
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public static OutboxMessage create(String topic, String messageKey, Message<?> message, String payload) {
        if (isBlank(topic) || isBlank(messageKey) || message == null || isBlank(payload)) {
            throw new IllegalArgumentException("topic, messageKey, message, payload 는 필수입니다");
        }

        return new OutboxMessage(topic, messageKey, message, payload);
    }

    public boolean isPending() {
        return status == OutboxStatus.PENDING;
    }

    public void markPublished(LocalDateTime now) {
        if (!isPending()) {
            return;
        }

        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
