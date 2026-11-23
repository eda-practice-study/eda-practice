package com.eda.order.infrastructure;

import com.eda.order.domain.ProductSnapshot;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductSnapshotRepository extends JpaRepository<ProductSnapshot, Long> {

    boolean existsByProductId(Long productId);

    List<ProductSnapshot> findAllByProductIdIn(Collection<Long> productIds);
}
