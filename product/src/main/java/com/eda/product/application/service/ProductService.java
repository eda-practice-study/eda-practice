package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.in.CreateProductCommand;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.application.port.out.PublishProductEventPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService implements CreateProductUseCase {

    private final SaveProductPort saveProductPort;
    private final PublishProductEventPort publishProductEventPort;

    @Override
    @Transactional
    public Product create(CreateProductCommand command) {
        Product product = Product.register(
                command.name(),
                command.price()
        );
        Product savedProduct = saveProductPort.save(product);
        ProductCreatedEvent event = new ProductCreatedEvent(savedProduct.getId());

        publishProductEventPort.publish(event);
        return savedProduct;
    }
}
