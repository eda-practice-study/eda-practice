package com.eda.order.adapter.out.persistence.catalog;

import com.eda.order.domain.catalog.ProductCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProductCatalogJpaRepository extends JpaRepository<ProductCatalogItem, Long> {

    List<ProductCatalogItem> findAllByProductIdIn(
            Collection<Long> productIds
    );
}