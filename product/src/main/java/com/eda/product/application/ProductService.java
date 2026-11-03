package com.eda.product.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.ProductCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.product.domain.Product;
import com.eda.product.domain.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public Product create(String name, BigDecimal price) {
        Product product = Product.register(name, price);
        try {
            product =  repository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_NAME_DUPLICATED
            );
        }

        outboxWriter.write(
                "product.events",
                String.valueOf(product.getId()),
                new ProductCreated(
                        product.getId(),
                        product.getName(),
                        product.getPrice().longValueExact()
                )
        );

        return product;
    }
}
