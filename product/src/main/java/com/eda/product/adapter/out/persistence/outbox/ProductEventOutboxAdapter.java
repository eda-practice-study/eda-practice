package com.eda.product.adapter.out.persistence.outbox;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.product.application.port.out.PublishProductEventPort;
import com.eda.product.domain.outbox.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 이벤트를 Kafka 로 바로 보내지 않고 outbox 테이블에 기록한다.
 * 호출자의 트랜잭션 안에서 INSERT 되므로 도메인 변경이 롤백되면 이벤트도 함께 사라진다.
 * 실제 발행은 {@code OutboxEventPublisher} 가 나중에 맡는다.
 */
@Component
@RequiredArgsConstructor
public class ProductEventOutboxAdapter implements PublishProductEventPort {

    private static final String AGGREGATE_TYPE = "PRODUCT";
    private static final String EVENT_TYPE_PRODUCT_CREATED = "PRODUCT_CREATED";

    private final OutboxEventJpaRepository outboxEventJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishCreated(ProductCreatedEvent event) {
        outboxEventJpaRepository.save(OutboxEvent.pending(
                AGGREGATE_TYPE,
                String.valueOf(event.productId()),
                EVENT_TYPE_PRODUCT_CREATED,
                serialize(event)));
    }

    private String serialize(ProductCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
