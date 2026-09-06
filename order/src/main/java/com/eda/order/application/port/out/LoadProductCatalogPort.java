package com.eda.order.application.port.out;

import com.eda.order.domain.catalog.ProductCatalogItem;

import java.util.Collection;
import java.util.List;

public interface LoadProductCatalogPort {

    List<ProductCatalogItem> loadAllByProductIds(Collection<Long> productIds);
}