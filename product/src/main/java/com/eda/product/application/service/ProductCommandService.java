package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.in.CreateProductCommand;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.application.port.out.PublishProductCreatedEventPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService implements CreateProductUseCase {

    private final SaveProductPort saveProductPort;
    private final PublishProductCreatedEventPort eventPublisher;

    @Override
    public Long create(CreateProductCommand command) {
        Product product = Product.register(
                command.name(),
                command.price()
        );

        Product savedProduct = saveProductPort.save(product);

        eventPublisher.publish(
                new ProductCreatedEvent(savedProduct.getId())
        );

        return savedProduct.getId();
    }
}
