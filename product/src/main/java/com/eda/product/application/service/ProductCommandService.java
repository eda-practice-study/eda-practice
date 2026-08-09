package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.in.RegisterProductUseCase;
import com.eda.product.application.port.out.PublishProductEventPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService implements RegisterProductUseCase {

    private final SaveProductPort saveProductPort;
    private final PublishProductEventPort publishProductEventPort;

    @Override
    public Long register(RegisterProductCommand command) {
        Product product = Product.register(command.name(), command.price());
        Product saved = saveProductPort.save(product);

        publishProductEventPort.publishCreated(
                ProductCreatedEvent.of(saved.getId(), saved.getName(), saved.getPrice()));

        return saved.getId();
    }
}
