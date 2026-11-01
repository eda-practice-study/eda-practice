package com.eda.product.service;

import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.product.domain.Product;
import com.eda.product.domain.ProductRepository;
import com.eda.product.exception.ProductNameDuplicatedException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String PRODUCT_EVENTS_TOPIC = "product.events";

    private final ProductRepository productRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public Product register(String name, BigDecimal price) {
        Product product = Product.register(name, price);
        if (productRepository.existsByName(name)) {
            throw new ProductNameDuplicatedException();
        }
        Product savedProduct = productRepository.save(product);

        outboxWriter.write(
                PRODUCT_EVENTS_TOPIC,
                savedProduct.getId().toString(),
                new ProductCreated(savedProduct.getId(), savedProduct.getName(), savedProduct.getPrice().longValueExact()));
        return savedProduct;
    }
}
