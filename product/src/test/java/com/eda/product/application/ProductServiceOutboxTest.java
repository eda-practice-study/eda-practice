package com.eda.product.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.ProductCreated;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.product.domain.Product;
import com.eda.product.domain.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
        "outbox.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:product-service-outbox;DB_CLOSE_DELAY=-1"
})
class ProductServiceOutboxTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @MockitoSpyBean
    private OutboxRepository outboxRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("상품 생성이 커밋되면 상품과 PENDING 상품 생성 이벤트가 함께 저장된다")
    void createWithOutbox() {
        Product product = productService.create("키보드", new BigDecimal("30000"));

        assertThat(productRepository.findById(product.getId())).isPresent();
        assertThat(productRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getTopic()).isEqualTo("product.events");
            assertThat(message.getMessageKey()).isEqualTo(product.getId().toString());
            assertThat(message.getMessageType()).isEqualTo("ProductCreated");
            assertThat(message.getMessageId()).isNotNull();
            assertThat(message.getOccurredAt()).isNotNull();
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getPublishedAt()).isNull();
            assertThat(jsonMapper.readValue(message.getPayload(), ProductCreated.class))
                    .isEqualTo(new ProductCreated(product.getId(), "키보드", 30000L));
        });
    }

    @Test
    @DisplayName("상품명 중복으로 실패하면 기존 상품과 이벤트만 남는다")
    void duplicateNameDoesNotAddOutbox() {
        Product original = productService.create("키보드", new BigDecimal("30000"));
        var originalMessageId = outboxRepository.findAll().getFirst().getMessageId();

        assertThatThrownBy(() -> productService.create("키보드", new BigDecimal("40000")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NAME_DUPLICATED));

        assertThat(productRepository.findAll()).singleElement().satisfies(product -> {
            assertThat(product.getId()).isEqualTo(original.getId());
            assertThat(product.getPrice()).isEqualByComparingTo("30000");
        });
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message ->
                assertThat(message.getMessageId()).isEqualTo(originalMessageId));
    }

    @Test
    @DisplayName("Outbox 저장 실패 시 이미 flush한 상품도 롤백하고 원래 예외를 전달한다")
    void outboxFailureRollsBackProduct() {
        var failure = new DataIntegrityViolationException("테스트용 Outbox 저장 실패");
        doAnswer(invocation -> {
            // 서비스 트랜잭션 안에서는 상품이 저장된 상태인지 먼저 확인한다.
            assertThat(productRepository.count()).isEqualTo(1);
            throw failure;
        }).when(outboxRepository).save(any(OutboxMessage.class));

        assertThatThrownBy(() -> productService.create("키보드", new BigDecimal("30000")))
                .isSameAs(failure);

        // 테스트 자체에는 트랜잭션이 없으므로 서비스 롤백 후의 DB 상태를 확인한다.
        assertThat(productRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }
}
