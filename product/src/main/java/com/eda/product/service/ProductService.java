package com.eda.product.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.outbox.OutboxWriter;
import com.eda.product.domain.Product;
import com.eda.product.dto.CreateProductRequest;
import com.eda.product.dto.CreateProductResponse;
import com.eda.product.event.ProductCreated;
import com.eda.product.repository.ProductRepository;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private static final String PRODUCT_EVENTS_TOPIC = "product.events";
    private static final String UNIQUE_VIOLATION = "23505";

    private final ProductRepository productRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public CreateProductResponse create(CreateProductRequest request) {
        Product product = Product.register(request.name(), request.price());
        try {
            productRepository.saveAndFlush(product);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMostSpecificCause() instanceof SQLException sqlException
                    && UNIQUE_VIOLATION.equals(sqlException.getSQLState())) {
                throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
            }
            throw ex;
        }

        long price = product.getPrice().longValueExact();
        outboxWriter.write(PRODUCT_EVENTS_TOPIC, product.getId().toString(),
                new ProductCreated(product.getId(), product.getName(), price));
        return new CreateProductResponse(product.getId(), product.getName(), price, product.getCreatedAt());
    }
}
