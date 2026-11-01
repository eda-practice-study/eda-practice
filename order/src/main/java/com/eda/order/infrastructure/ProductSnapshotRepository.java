package com.eda.order.infrastructure;

import com.eda.order.domain.ProductSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSnapshotRepository extends JpaRepository<ProductSnapshot, Long> {

    boolean existsByProductId(Long productId);
}
