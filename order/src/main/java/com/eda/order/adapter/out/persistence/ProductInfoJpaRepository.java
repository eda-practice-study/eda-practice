package com.eda.order.adapter.out.persistence;

import com.eda.order.domain.ProductInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductInfoJpaRepository extends JpaRepository<ProductInfo, Long> {

    List<ProductInfo> findAllByProductIdIn(List<Long> productIds);
}
