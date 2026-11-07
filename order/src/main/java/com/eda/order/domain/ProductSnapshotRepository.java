package com.eda.order.domain;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductSnapshotRepository extends JpaRepository<ProductSnapshot, Long> {

    @Modifying
    @Query(value = """
            insert into product_snapshot (product_id, name, price, created_at, updated_at)
            values (:productId, :name, :price, current_timestamp, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int register(Long productId, String name, BigDecimal price);
}
