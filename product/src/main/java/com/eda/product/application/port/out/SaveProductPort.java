package com.eda.product.application.port.out;

import com.eda.product.domain.Product;

public interface SaveProductPort {

    Product save(Product product);
}
