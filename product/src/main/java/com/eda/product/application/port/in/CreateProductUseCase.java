package com.eda.product.application.port.in;

import com.eda.product.domain.Product;

public interface CreateProductUseCase {
    Product create(CreateProductCommand command);
}
