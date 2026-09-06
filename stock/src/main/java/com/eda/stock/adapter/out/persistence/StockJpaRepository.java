package com.eda.stock.adapter.out.persistence;

import com.eda.stock.domain.Stock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StockJpaRepository extends JpaRepository<Stock, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from Stock s
            where s.productId = :productId
            """)
    Optional<Stock> findByProductIdForUpdate(
            @Param("productId") Long productId
    );
}
