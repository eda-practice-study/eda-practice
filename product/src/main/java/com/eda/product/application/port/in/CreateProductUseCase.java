package com.eda.product.application.port.in;

import com.eda.product.adapter.in.web.dto.CreateProductRequest;

public interface CreateProductUseCase {

    void register(CreateProductRequest createProductRequest);
}
