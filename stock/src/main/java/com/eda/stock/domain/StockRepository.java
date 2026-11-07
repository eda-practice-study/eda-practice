package com.eda.stock.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.productId = :productId")
    Optional<Stock> findForUpdate(Long productId);

    @Modifying
    @Query(value = """
            insert into stock (product_id, quantity, version, created_at, updated_at)
            values (:productId, 0, 0, current_timestamp, current_timestamp)
            on conflict do nothing
            """, nativeQuery = true)
    int initialize(Long productId);
}
