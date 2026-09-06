package com.eda.order.adapter.out.persistence;

import com.eda.order.domain.ProductInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductInfoJpaRepository extends JpaRepository<ProductInfo, Long> {

    Optional<ProductInfo> findByProductId(Long productId);
}
