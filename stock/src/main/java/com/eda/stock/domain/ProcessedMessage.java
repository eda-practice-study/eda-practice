package com.eda.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "processed_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMessage implements Persistable<UUID> {

    @Id
    private UUID messageId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    public ProcessedMessage(UUID messageId) {
        this.messageId = messageId;
    }

    @Override
    public UUID getId() {
        return messageId;
    }

    @Override
    @Transient
    public boolean isNew() {
        // UUID를 직접 부여해도 merge가 아닌 INSERT로 유일 제약을 확인한다.
        return processedAt == null;
    }
}
