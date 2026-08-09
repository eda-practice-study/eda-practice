package com.eda.product.application.service;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.product.application.port.in.RegisterProductUseCase.RegisterProductCommand;
import com.eda.product.application.port.out.SaveProductPort;
import com.eda.product.domain.Product;
import com.eda.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductCommandServiceTest {

    private FakeProductStore saveProductPort;
    private ProductCommandService productCommandService;

    @BeforeEach
    void setUp() {
        saveProductPort = new FakeProductStore();
        productCommandService = new ProductCommandService(saveProductPort);
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
    @DisplayName("도메인 검증에 실패하면 저장되지 않는다")
    void doesNotSaveWhenDomainValidationFails() {
        // when & then
        assertThatThrownBy(() -> productCommandService.register(new RegisterProductCommand("티셔츠", BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_ERROR);

        assertThat(saveProductPort.count()).isZero();
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
}