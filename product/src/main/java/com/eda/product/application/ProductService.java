package com.eda.product.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.product.domain.Product;
import com.eda.product.domain.ProductRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String TOPIC = "product.events";

    private final ProductRepository productRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public Product create(String name, BigDecimal price) {
        Product product = Product.register(name, price);
        if (productRepository.existsByName(product.getName())) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }

        Product saved = save(product);
        outboxWriter.write(TOPIC, String.valueOf(saved.getId()),
                new ProductCreated(saved.getId(), saved.getName(), saved.getPrice().longValueExact()));
        return saved;
    }

    // existsByName 과 저장 사이에 같은 이름이 먼저 저장되면 unique 제약이 막는다
    private Product save(Product product) {
        try {
            return productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }
    }
}
