package com.eda.product.application.service;

import com.eda.common.event.AggregateType;
import com.eda.common.event.EventTypes;
import com.eda.common.event.ProductCreated;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.application.port.out.SaveOutboxEventPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.OutboxEvent;
import com.eda.product.domain.Product;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService implements CreateProductUseCase {

    private final SaveProductPort saveProductPort;
    private final SaveOutboxEventPort saveOutboxEventPort;
    private final ObjectMapper objectMapper;

    @Override
    public Long create(String productName, BigDecimal price) {
        Product product = Product.register(productName, price);

        // DB에 저장
        Product saved = saveProductPort.save(product);
        log.info("상품 생성 완료 productId={}, name={}, price={}", saved.getId(), saved.getName(), saved.getPrice());

        // 같은 트랜잭션 안에서 outbox에 이벤트 저장
        String payload = objectMapper.writeValueAsString(
                new ProductCreated(saved.getId(), saved.getName(), saved.getPrice())
        );
        saveOutboxEventPort.save(OutboxEvent.create(AggregateType.PRODUCT, saved.getId(), EventTypes.ProductCreated, payload));
        log.info("Outbox 이벤트 저장 완료 productId={}, eventType={}", saved.getId(), EventTypes.ProductCreated);

        return saved.getId();
    }
}
