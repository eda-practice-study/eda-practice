package com.eda.product.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
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

    @Transactional
    public Product create(String name, BigDecimal price) {
        Product product = Product.register(name, price);
        try {
            return repository.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_NAME_DUPLICATED
            );
        }
    }
}
