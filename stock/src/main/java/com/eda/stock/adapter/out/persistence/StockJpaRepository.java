package com.eda.stock.adapter.out.persistence;

import com.eda.stock.domain.Stock;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

interface StockJpaRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select s from Stock s where s.productId = :productId")
    Optional<Stock> findByProductIdForUpdate(@Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select s from Stock s where s.productId in :productIds order by s.productId")
    List<Stock> findAllByProductIdsForUpdate(@Param("productIds") Collection<Long> productIds);
}
