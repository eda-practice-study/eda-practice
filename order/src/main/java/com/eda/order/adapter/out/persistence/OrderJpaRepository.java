package com.eda.order.adapter.out.persistence;

import com.eda.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderJpaRepository extends JpaRepository<Order, Long> {
}
