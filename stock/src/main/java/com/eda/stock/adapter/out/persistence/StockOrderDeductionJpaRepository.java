package com.eda.stock.adapter.out.persistence;

import com.eda.stock.domain.StockOrderDeduction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockOrderDeductionJpaRepository extends JpaRepository<StockOrderDeduction, Long> {

    Optional<StockOrderDeduction> findByOrderId(Long orderId);
}
