package com.eda.product.adapter.out.persistence;

import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductPersistenceAdapter implements SaveProductPort {

    private final ProductJpaRepository repository;

    @Override
    public Product save(Product product) {
        return repository.save(product);
    }
}
