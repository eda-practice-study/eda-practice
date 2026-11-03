package com.eda.stock.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByProductId(Long productId);

    @Modifying
    @Query(value = """
        INSERT INTO stock (
                           product_id,
                           quantity,
                           created_at,
                           updated_at
                           )
        VALUES (
                :productId,
                :quantity,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
                )
        ON CONFLICT (product_id) DO NOTHING """, nativeQuery = true)
    int insertIfAbsent(
            @Param("productId") Long productId,
            @Param("quantity") int quantity
    );
}
