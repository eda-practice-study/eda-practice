package com.eda.stock.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.StockInitialized;
import com.eda.common.outbox.OutboxMessage;
import com.eda.common.outbox.OutboxRepository;
import com.eda.common.outbox.OutboxStatus;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:stock-service-outbox;DB_CLOSE_DELAY=-1")
class StockServiceOutboxTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @MockitoSpyBean
    private OutboxRepository outboxRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        stockRepository.deleteAll();
    }

    @Test
    @DisplayName("새 재고와 PENDING 재고 등록 이벤트를 함께 커밋한다")
    void initializeWithOutbox() {
        Stock stock = stockService.initializeStock(1L);

        assertThat(stockRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getId()).isEqualTo(stock.getId());
            assertThat(saved.getProductId()).isEqualTo(1L);
            assertThat(saved.getQuantity()).isZero();
            assertThat(saved.getCreatedAt()).isNotNull();
        });
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getTopic()).isEqualTo("stock.events");
            assertThat(message.getMessageKey()).isEqualTo("1");
            assertThat(message.getMessageType()).isEqualTo("StockInitialized");
            assertThat(message.getMessageId()).isNotNull();
            assertThat(message.getOccurredAt()).isNotNull();
            assertThat(message.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(message.getPublishedAt()).isNull();
            assertThat(jsonMapper.readValue(message.getPayload(), StockInitialized.class))
                    .isEqualTo(new StockInitialized(1L, 0));
        });
    }

    @Test
    @DisplayName("기존 재고 수량을 유지하고 등록 이벤트를 추가 생성하지 않는다")
    void preserveExistingStock() {
        Stock original = stockService.initializeStock(1L);
        var originalMessageId = outboxRepository.findAll().getFirst().getMessageId();
        original.increase(10);
        stockRepository.saveAndFlush(original);

        Stock result = stockService.initializeStock(1L);

        assertThat(result.getId()).isEqualTo(original.getId());
        assertThat(result.getQuantity()).isEqualTo(10);
        assertThat(stockRepository.findAll()).singleElement().satisfies(stock ->
                assertThat(stock.getQuantity()).isEqualTo(10));
        assertThat(outboxRepository.findAll()).singleElement().satisfies(message -> {
            assertThat(message.getMessageId()).isEqualTo(originalMessageId);
            assertThat(jsonMapper.readValue(message.getPayload(), StockInitialized.class).quantity())
                    .isZero();
        });
    }

    @Test
    @DisplayName("Outbox 저장 실패 시 먼저 저장한 재고도 롤백한다")
    void outboxFailureRollsBackStock() {
        var failure = new DataIntegrityViolationException("테스트용 Outbox 저장 실패");
        doAnswer(invocation -> {
            assertThat(stockRepository.count()).isEqualTo(1);
            throw failure;
        }).when(outboxRepository).save(any(OutboxMessage.class));

        assertThatThrownBy(() -> stockService.initializeStock(1L)).isSameAs(failure);

        // 테스트에 트랜잭션을 붙이지 않아 서비스 롤백 후의 DB 상태를 확인한다.
        assertThat(stockRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("상품 ID가 없으면 재고와 이벤트를 저장하지 않는다")
    void rejectMissingProductId() {
        assertThatThrownBy(() -> stockService.initializeStock(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        assertThat(stockRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }
}
