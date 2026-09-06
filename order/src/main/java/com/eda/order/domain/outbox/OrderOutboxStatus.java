package com.eda.order.domain.outbox;

public enum OrderOutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}