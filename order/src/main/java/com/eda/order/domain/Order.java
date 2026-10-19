package com.eda.order.domain;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    public static final int MAX_LINES = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, comment = "주문 회원 ID")
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, comment = "주문 상태")
    private OrderStatus status;

    @Column(nullable = false, precision = 19, scale = 0, comment = "주문 총액")
    private BigDecimal totalAmount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLine> orderLines = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false, comment = "생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false, comment = "수정 시각")
    private LocalDateTime updatedAt;

    private Order(Long memberId, List<OrderLine> orderLines) {
        this.memberId = memberId;
        this.orderLines = orderLines;
        this.status = OrderStatus.CREATED;
        this.totalAmount = orderLines.stream()
                .map(OrderLine::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Order create(Long memberId, List<LineItem> items) {
        if (memberId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (items == null || items.isEmpty() || items.size() > MAX_LINES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (items.stream().map(LineItem::productId).distinct().count() != items.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        List<OrderLine> orderLines = items.stream()
                .map(item -> OrderLine.of(item.productId(), item.productName(), item.unitPrice(), item.quantity()))
                .collect(Collectors.toCollection(ArrayList::new));

        return new Order(memberId, orderLines);
    }

    public List<OrderLine> getOrderLines() {
        return List.copyOf(orderLines);
    }

    public record LineItem(Long productId, String productName, BigDecimal unitPrice, int quantity) {
    }
}
