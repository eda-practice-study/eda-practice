package com.eda.stock.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

// 주문 하나 때문에 차감한 DB 재고. order_id unique 가 같은 주문 생성 이벤트의 중복 차감을 막는다.
@Entity
@Table(name = "stock_decrease")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockDecrease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, comment = "주문 ID")
    private Long orderId;

    @Column(nullable = false, length = 36, comment = "Redis 차감에 쓴 주문 키")
    private String orderKey;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "stock_decrease_id", nullable = false)
    private List<StockDecreaseLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false, comment = "생성 시각")
    private LocalDateTime createdAt;

    private StockDecrease(Long orderId, String orderKey, List<StockDecreaseLine> lines) {
        this.orderId = orderId;
        this.orderKey = orderKey;
        this.lines = lines;
    }

    public static StockDecrease of(Long orderId, String orderKey, List<StockDecreaseLine> lines) {
        return new StockDecrease(orderId, orderKey, new ArrayList<>(lines));
    }

    public List<StockDecreaseLine> getLines() {
        return List.copyOf(lines);
    }
}
