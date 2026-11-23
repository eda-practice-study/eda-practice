package com.eda.stock.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

// 재고 차감을 마친 주문. 같은 주문 생성 이벤트를 다시 받아도 한 번만 차감하기 위한 기록
@Entity
@Table(name = "processed_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, comment = "주문 ID")
    private Long orderId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false, comment = "처리 시각")
    private LocalDateTime processedAt;

    private ProcessedOrder(Long orderId) {
        this.orderId = orderId;
    }

    public static ProcessedOrder of(Long orderId) {
        return new ProcessedOrder(orderId);
    }
}
