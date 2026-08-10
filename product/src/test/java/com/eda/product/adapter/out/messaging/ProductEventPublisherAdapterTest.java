package com.eda.product.adapter.out.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.eda.common.event.EventTopics;
import com.eda.common.event.ProductCreatedEvent;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class ProductEventPublisherAdapterTest {

    private KafkaTemplate<String, Object> kafkaTemplate;
    private ProductEventPublisherAdapter productEventPublisherAdapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(future);

        productEventPublisherAdapter = new ProductEventPublisherAdapter(kafkaTemplate);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션이 없으면 즉시 발행한다")
    void publishImmediatelyWithoutTransaction() {
        // when
        productEventPublisherAdapter.publishCreated(event());

        // then
        then(kafkaTemplate).should().send(eq(EventTopics.PRODUCT_EVENTS), eq("1"), any(ProductCreatedEvent.class));
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋 전까지 발행하지 않는다")
    void doNotPublishBeforeCommit() {
        // given
        TransactionSynchronizationManager.initSynchronization();

        // when
        productEventPublisherAdapter.publishCreated(event());

        // then: 롤백되면 이 이벤트는 영원히 나가지 않는다
        then(kafkaTemplate).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("커밋이 완료되면 발행한다")
    void publishAfterCommit() {
        // given
        TransactionSynchronizationManager.initSynchronization();
        productEventPublisherAdapter.publishCreated(event());

        // when
        TransactionSynchronizationUtils.triggerAfterCommit();

        // then
        then(kafkaTemplate).should().send(eq(EventTopics.PRODUCT_EVENTS), eq("1"), any(ProductCreatedEvent.class));
    }

    @Test
    @DisplayName("메시지 키는 productId 이므로 같은 상품은 같은 파티션으로 간다")
    void useProductIdAsMessageKey() {
        // when
        productEventPublisherAdapter.publishCreated(ProductCreatedEvent.of(42L, "티셔츠", BigDecimal.valueOf(10000)));

        // then
        then(kafkaTemplate).should().send(anyString(), eq("42"), any(ProductCreatedEvent.class));
    }

    private ProductCreatedEvent event() {
        return ProductCreatedEvent.of(1L, "티셔츠", BigDecimal.valueOf(10000));
    }
}
