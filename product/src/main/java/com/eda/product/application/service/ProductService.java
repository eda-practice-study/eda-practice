package com.eda.product.application.service;

import com.eda.common.event.ProductCreated;
import com.eda.common.event.Topics;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.application.port.out.PublishEventPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService implements CreateProductUseCase {

    private final PublishEventPort publishEventPort;
    private final SaveProductPort saveProductPort;

    @Override
    public Long create(String productName, BigDecimal price) {
        Product product = Product.register(productName, price);

        // DB에 저장
        Product saved = saveProductPort.save(product);
        log.info("상품 생성 완료 productId={}, name={}, price={}", saved.getId(), saved.getName(), saved.getPrice());

        // 메시지 발송
        log.info("ProductCreated 발송 요청 productId={}, topic={}", saved.getId(), Topics.PRODUCT_EVENTS);
        publishEventPort.publish(new ProductCreated(saved.getId()));

        return saved.getId();
    }
}
