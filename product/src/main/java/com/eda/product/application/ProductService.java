package com.eda.product.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.product.api.dto.CreateProductRequest;
import com.eda.product.api.dto.CreateProductResponse;
import com.eda.product.domain.Product;
import com.eda.product.domain.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final OutboxWriter outboxWriter;

    // 상품 생성 + 생성 이벤트 저장
    @Transactional
    public CreateProductResponse createProduct(CreateProductRequest request) {

        // 상품명 중복여부 체크
        if (productRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.PRODUCT_NAME_DUPLICATED);
        }

        // 상품 정보 저장
        Product product = Product.register(request.name(), request.price());
        productRepository.save(product);

        // 이벤트 저장
        outboxWriter.write(
                "product.events",
                product.getId().toString(),
                new ProductCreated(product.getId(), product.getName(), product.getPrice())
        );

        return CreateProductResponse.from(product);
    }
}
