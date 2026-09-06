package com.eda.product.application.service;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.adapter.in.web.dto.CreateProductRequest;
import com.eda.product.application.port.in.CreateProductUseCase;
import com.eda.product.application.port.out.SaveOutboxEventPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import com.eda.product.domain.outbox.AggregateType;
import com.eda.product.domain.outbox.EventType;
import com.eda.product.domain.outbox.OutboxEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ProductCommandService implements CreateProductUseCase {

    private final SaveProductPort saveProductPort;
    private final SaveOutboxEventPort saveOutboxEventPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void register(CreateProductRequest createProductRequest) {
        // 1) 상품 등록
        Product product = saveProductPort.save(Product.register(createProductRequest.name(), createProductRequest.price()));

        // 2) 상품 생성 이벤트 - payload
        ProductCreatedEvent event = new ProductCreatedEvent(
                product.getId(),
                product.getName(),
                product.getPrice()
        );

        // 3) 이벤트 -> JSON payload로 변환
        String payload = objectMapper.writeValueAsString(event);

        // 4) outbox 이벤트 생성
        OutboxEvent outboxEvent = OutboxEvent.create(AggregateType.PRODUCT, product.getId(), EventType.CREATED, payload);

        // 5) outbox 저장
        saveOutboxEventPort.save(outboxEvent);
    }
}
