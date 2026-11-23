package com.eda.stock.infrastructure;

import com.eda.stock.domain.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, Long> {

    boolean existsByOrderId(Long orderId);
}
