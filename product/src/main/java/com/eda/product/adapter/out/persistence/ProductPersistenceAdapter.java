package com.eda.product.adapter.out.persistence;

import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ProductPersistenceAdapter implements SaveProductPort {

    private final ProductJpaRepository jpaRepository;

    @Override
    public Product save(Product product) {
        return jpaRepository.save(product);
    }
}
