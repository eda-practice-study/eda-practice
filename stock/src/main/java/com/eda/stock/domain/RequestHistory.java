package com.eda.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "request_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestHistory implements Persistable<RequestHistoryId> {

    @EmbeddedId
    private RequestHistoryId id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public RequestHistory(String requestType, String idempotencyKey) {
        this.id = new RequestHistoryId(requestType, idempotencyKey);
    }

    @Override
    @Transient
    public boolean isNew() {
        // 식별자가 이미 있어도 INSERT로 복합 PK 중복을 확인한다.
        return createdAt == null;
    }
}
