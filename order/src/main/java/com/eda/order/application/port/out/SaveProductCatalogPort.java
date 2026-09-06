package com.eda.order.application.port.out;

import com.eda.order.domain.catalog.ProductCatalogItem;

public interface SaveProductCatalogPort {

    ProductCatalogItem save(ProductCatalogItem product);
}