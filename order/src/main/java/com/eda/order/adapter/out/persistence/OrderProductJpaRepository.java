package com.eda.order.adapter.out.persistence;

import com.eda.order.domain.OrderProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderProductJpaRepository extends JpaRepository<OrderProduct, Long> {
    Optional<OrderProduct> findByProductId(Long productId);
    List<OrderProduct> findByProductIdIn(Collection<Long> productIds);
}
