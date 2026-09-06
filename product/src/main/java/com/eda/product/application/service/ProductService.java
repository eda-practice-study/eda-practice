package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.in.CreateProductCommand;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.application.port.out.SaveOutboxPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService implements CreateProductUseCase {

    private final SaveProductPort saveProductPort;
    private final SaveOutboxPort saveOutboxPort;

    @Override
    @Transactional
    public Product create(CreateProductCommand command) {
        Product product = Product.register(
                command.name(),
                command.price()
        );
        Product savedProduct = saveProductPort.save(product);

        ProductCreatedEvent event = new ProductCreatedEvent(
                UUID.randomUUID(),
                savedProduct.getId(),
                Instant.now()
        );

        saveOutboxPort.save(event);
        return savedProduct;
    }
}
