package com.eda.product.adapter.out.persistence.outbox;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.domain.outbox.OutboxEvent;
import com.eda.product.domain.outbox.OutboxStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

@DataJpaTest
@Import({ProductEventOutboxAdapter.class, ObjectMapper.class})
class ProductEventOutboxAdapterTest {

    @Autowired
    private ProductEventOutboxAdapter productEventOutboxAdapter;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("상품 생성 이벤트를 발행하면 outbox에 미발행 상태로 기록된다")
    void recordProductCreatedEventAsPending() {
        // when
        productEventOutboxAdapter.publishCreated(ProductCreatedEvent.of(1L, "티셔츠", BigDecimal.valueOf(10000)));

        // then
        assertThat(outboxEventJpaRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getAggregateType()).isEqualTo("PRODUCT");
            assertThat(saved.getAggregateId()).isEqualTo("1");
            assertThat(saved.getEventType()).isEqualTo("PRODUCT_CREATED");
            assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(saved.getPublishedAt()).isNull();
        });
    }

    @Test
    @DisplayName("payload에는 이벤트 내용이 JSON으로 그대로 담긴다")
    void storeEventAsJsonPayload() {
        // given
        ProductCreatedEvent event = ProductCreatedEvent.of(42L, "청바지", BigDecimal.valueOf(39000));

        // when
        productEventOutboxAdapter.publishCreated(event);

        // then: 저장된 payload 를 되돌리면 원본과 같아야 한다. 이 문자열이 그대로 Kafka 로 나간다
        OutboxEvent saved = outboxEventJpaRepository.findAll().getFirst();
        assertThat(objectMapper.readValue(saved.getPayload(), ProductCreatedEvent.class)).isEqualTo(event);
    }

    @Test
    @DisplayName("메시지 키로 쓰이는 aggregateId는 productId 다")
    void useProductIdAsAggregateId() {
        // when
        productEventOutboxAdapter.publishCreated(ProductCreatedEvent.of(42L, "청바지", BigDecimal.valueOf(39000)));

        // then: 같은 상품의 이벤트가 같은 파티션으로 가려면 키가 productId 여야 한다
        assertThat(outboxEventJpaRepository.findAll().getFirst().getAggregateId()).isEqualTo("42");
    }
}
