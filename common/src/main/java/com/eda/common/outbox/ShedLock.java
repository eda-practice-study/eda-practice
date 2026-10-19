package com.eda.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shedlock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ShedLock {

    @Id
    @Column(length = 64)
    private String name;

    @Column(nullable = false)
    private LocalDateTime lockUntil;

    @Column(nullable = false)
    private LocalDateTime lockedAt;

    @Column(nullable = false)
    private String lockedBy;
}
