package com.eda.product.application.service;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.product.application.port.in.RegisterProductUseCase.RegisterProductCommand;
import com.eda.product.application.port.out.PublishProductEventPort;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import com.eda.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductCommandServiceTest {

    private FakeProductStore saveProductPort;
    private FakeEventPublisher publishProductEventPort;
    private ProductCommandService productCommandService;

    @BeforeEach
    void setUp() {
        saveProductPort = new FakeProductStore();
        publishProductEventPort = new FakeEventPublisher();
        productCommandService = new ProductCommandService(saveProductPort, publishProductEventPort);
    }

    @Test
    @DisplayName("상품을 등록하면 저장하고 생성된 ID를 반환한다")
    void registerProduct() {
        // when
        Long productId = productCommandService.register(new RegisterProductCommand("티셔츠", BigDecimal.valueOf(10000)));

        // then
        assertThat(productId).isNotNull();

        Product saved = saveProductPort.findById(productId);
        assertThat(saved.getName()).isEqualTo("티셔츠");
        assertThat(saved.getPrice()).isEqualByComparingTo("10000");
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("상품을 등록하면 저장된 ID로 상품 생성 이벤트를 발행한다")
    void publishProductCreatedEvent() {
        // when
        Long productId = productCommandService.register(new RegisterProductCommand("티셔츠", BigDecimal.valueOf(10000)));

        // then
        assertThat(publishProductEventPort.published).hasSize(1);

        ProductCreatedEvent event = publishProductEventPort.published.getFirst();
        assertThat(event.productId()).isEqualTo(productId);
        assertThat(event.name()).isEqualTo("티셔츠");
        assertThat(event.price()).isEqualByComparingTo("10000");
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("도메인 검증에 실패하면 저장도 발행도 하지 않는다")
    void doesNotSaveOrPublishWhenDomainValidationFails() {
        // when & then
        assertThatThrownBy(() -> productCommandService.register(new RegisterProductCommand("티셔츠", BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThat(saveProductPort.count()).isZero();
        assertThat(publishProductEventPort.published).isEmpty();
    }

    /**
     * JPA 없이 저장소를 흉내내는 가짜 어댑터. 저장 시 ID를 채번하는 것까지 실제와 동일하게 맞춘다.
     */
    private static class FakeProductStore implements SaveProductPort {

        private final Map<Long, Product> store = new HashMap<>();
        private long sequence = 0L;

        @Override
        public Product save(Product product) {
            ReflectionTestUtils.setField(product, "id", ++sequence);
            store.put(product.getId(), product);
            return product;
        }

        Product findById(Long id) {
            return store.get(id);
        }

        int count() {
            return store.size();
        }
    }

    /**
     * 발행된 이벤트를 모아두기만 하는 가짜 어댑터. Kafka 없이 발행 여부와 내용만 검증한다.
     */
    private static class FakeEventPublisher implements PublishProductEventPort {

        private final List<ProductCreatedEvent> published = new ArrayList<>();

        @Override
        public void publishCreated(ProductCreatedEvent event) {
            published.add(event);
        }
    }
}