package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.product.application.port.in.CreateProductCommand;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.application.port.out.OutboxEventPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import com.eda.product.domain.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductCommandService implements CreateProductUseCase {

    private final SaveProductPort saveProductPort;
    private final OutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

    @Override
    public Long create(CreateProductCommand command) {
        Product product = Product.register(
                command.name(),
                command.price()
        );

        Product savedProduct = saveProductPort.save(product);

        ProductCreatedEvent event = new ProductCreatedEvent(savedProduct.getId(), savedProduct.getName(), savedProduct.getPrice());

        String payload = serialize(event);

        OutboxEvent outboxEvent = OutboxEvent.pending(
                "PRODUCT",
                savedProduct.getId().toString(),
                "PRODUCT_CREATED",
                payload
        );

        outboxEventPort.save(outboxEvent);

        return savedProduct.getId();
    }

    private String serialize(ProductCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
