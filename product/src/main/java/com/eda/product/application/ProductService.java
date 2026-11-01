package com.eda.product.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.Topics;
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

    private final ProductRepository productRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public Product create(String name, BigDecimal price) {
        validateUniqueName(name);

        Product product = Product.create(name, price);
        productRepository.save(product);
        outboxWriter.write(Topics.PRODUCT_EVENTS, String.valueOf(product.getId()),
                new ProductCreated(product.getId(), product.getName(), product.getPrice()));

        return product;
    }

    private void validateUniqueName(String name) {
        if (productRepository.existsByName(name)) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }
    }
}
