package com.eda.order.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSnapshotRepository extends JpaRepository<ProductSnapshot, Long> {
    boolean existsByProductId(Long productId);

    Optional<ProductSnapshot> findByProductId(Long productId);
}
