package com.eda.product.application.service;

import com.eda.product.adapter.in.web.dto.CreateProductRequest;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductCommandService implements CreateProductUseCase {

    private final SaveProductPort saveProductPort;


    @Override
    @Transactional
    public void register(CreateProductRequest createProductRequest) {
        // 1) 상품 등록
        Product product = saveProductPort.save(Product.register(createProductRequest.name(), createProductRequest.price()));

        // 2) 재고 등록 - 이벤트 발행
    }
}
