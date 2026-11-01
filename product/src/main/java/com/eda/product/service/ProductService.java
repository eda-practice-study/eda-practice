package com.eda.product.service;

import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.product.domain.Product;
import com.eda.product.domain.ProductRepository;
import com.eda.product.exception.ProductNameDuplicatedException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
        Product savedProduct;
        try {
            // 커밋 전에 상품명 유일 제약 위반을 확인해 도메인 예외로 변환한다.
            savedProduct = productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateName(ex)) {
                throw new ProductNameDuplicatedException();
            }
            throw ex;
        }

        outboxWriter.write(
                PRODUCT_EVENTS_TOPIC,
                savedProduct.getId().toString(),
                new ProductCreated(savedProduct.getId(), savedProduct.getName(), savedProduct.getPrice().longValueExact()));
        return savedProduct;
    }

    private boolean isDuplicateName(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && Product.NAME_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
