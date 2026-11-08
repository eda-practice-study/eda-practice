package com.eda.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestHistoryId implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int MAX_KEY_LENGTH = 128;

    @Column(nullable = false, updatable = false, length = 64)
    private String requestType;

    @Column(nullable = false, updatable = false, length = MAX_KEY_LENGTH)
    private String idempotencyKey;

    public RequestHistoryId(String requestType, String idempotencyKey) {
        this.requestType = requestType;
        this.idempotencyKey = idempotencyKey;
    }
}
