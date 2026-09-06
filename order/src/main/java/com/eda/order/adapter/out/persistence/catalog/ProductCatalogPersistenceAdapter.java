package com.eda.order.adapter.out.persistence.catalog;

import com.eda.order.application.port.out.LoadProductCatalogPort;
import com.eda.order.application.port.out.SaveProductCatalogPort;
import com.eda.order.domain.catalog.ProductCatalogItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductCatalogPersistenceAdapter implements LoadProductCatalogPort, SaveProductCatalogPort {

    private final ProductCatalogJpaRepository repository;

    @Override
    public List<ProductCatalogItem> loadAllByProductIds(
            Collection<Long> productIds
    ) {
        return repository.findAllByProductIdIn(productIds);
    }

    @Override
    public ProductCatalogItem save(ProductCatalogItem product) {
        return repository.save(product);
    }
}