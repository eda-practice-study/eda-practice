package com.eda.product.application.service;

import com.eda.product.adapter.in.web.dto.CreateProductRequest;
import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.application.port.out.PublishProductEventPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductCommandService implements CreateProductUseCase {

    private final SaveProductPort saveProductPort;
    private final PublishProductEventPort publishProductEventPort;


    @Override
    @Transactional
    public void register(CreateProductRequest createProductRequest) {
        // 1) 상품 등록
        Product product = saveProductPort.save(Product.register(createProductRequest.name(), createProductRequest.price()));

        // 2) 재고 등록 - 이벤트 발행
        publishProductEventPort.publish(new ProductCreatedEvent(product.getId()));
    }
}
