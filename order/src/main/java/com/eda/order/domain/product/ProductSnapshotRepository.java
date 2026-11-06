package com.eda.order.domain.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSnapshotRepository extends JpaRepository<ProductSnapshot, Long> {

    Boolean existsByProductId(Long productId);
}
