package com.eda.order.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSnapshotRepository extends JpaRepository<ProductSnapshot, Long> {

    Optional<ProductSnapshot> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    List<ProductSnapshot> findAllByProductIdIn(Collection<Long> productIds);
}
