package com.eda.stock.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eda.common.message.stock.StockCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.ProcessedMessage;
import com.eda.stock.domain.ProcessedMessageRepository;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.exception.InvalidProductEventException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProcessedMessageRepository processedMessageRepository;
    @Mock
    private OutboxWriter outboxWriter;
    @InjectMocks
    private StockService stockService;

    @Test
    @DisplayName("처리 이력과 수량이 0인 재고를 저장하고 상품 ID를 키로 생성 이벤트를 기록한다")
    void createFor_shouldStoreEvent_whenProductNew() {
        UUID messageId = UUID.randomUUID();
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean created = stockService.createFor(messageId, 1L);

        ArgumentCaptor<ProcessedMessage> history = ArgumentCaptor.forClass(ProcessedMessage.class);
        ArgumentCaptor<Stock> stock = ArgumentCaptor.forClass(Stock.class);
        InOrder order = inOrder(processedMessageRepository, stockRepository, outboxWriter);
        order.verify(processedMessageRepository).existsById(messageId);
        order.verify(processedMessageRepository).save(history.capture());
        order.verify(stockRepository).existsByProductId(1L);
        order.verify(stockRepository).save(stock.capture());
        order.verify(outboxWriter).write("stock.events", "1", new StockCreated(1L, 0));
        assertThat(created).isTrue();
        assertThat(history.getValue().getMessageId()).isEqualTo(messageId);
        assertThat(history.getValue().isNew()).isTrue();
        assertThat(stock.getValue().getProductId()).isEqualTo(1L);
        assertThat(stock.getValue().getQuantity()).isZero();
    }

    @Test
    @DisplayName("이미 처리한 메시지는 재고에 접근하지 않고 건너뛴다")
    void createFor_shouldSkip_whenMessageAlreadyProcessed() {
        UUID messageId = UUID.randomUUID();
        when(processedMessageRepository.existsById(messageId)).thenReturn(true);

        boolean created = stockService.createFor(messageId, 1L);

        assertThat(created).isFalse();
        verify(processedMessageRepository).existsById(messageId);
        verifyNoMoreInteractions(processedMessageRepository);
        verifyNoInteractions(stockRepository, outboxWriter);
    }

    @Test
    @DisplayName("새 메시지라도 이미 존재하는 상품 재고를 덮어쓰지 않는다")
    void createFor_shouldPreserveStock_whenProductAlreadyExists() {
        UUID messageId = UUID.randomUUID();
        when(stockRepository.existsByProductId(1L)).thenReturn(true);

        boolean created = stockService.createFor(messageId, 1L);

        assertThat(created).isFalse();
        verify(processedMessageRepository).save(any(ProcessedMessage.class));
        verify(stockRepository).existsByProductId(1L);
        verify(stockRepository, never()).save(any());
        verifyNoInteractions(outboxWriter);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("상품 ID가 없거나 양수가 아니면 저장하지 않는다")
    void createFor_shouldReject_whenProductIdInvalid(Long productId) {
        assertThatThrownBy(() -> stockService.createFor(UUID.randomUUID(), productId))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(stockRepository, processedMessageRepository, outboxWriter);
    }

    @Test
    @DisplayName("메시지 ID가 없으면 저장하지 않는다")
    void createFor_shouldReject_whenMessageIdMissing() {
        assertThatThrownBy(() -> stockService.createFor(null, 1L))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(stockRepository, processedMessageRepository, outboxWriter);
    }

    @Test
    @DisplayName("이력 저장 중 동시 중복이 감지되면 재시도할 수 있도록 예외를 전파한다")
    void createFor_shouldPropagate_whenHistorySaveFails() {
        var failure = new DataIntegrityViolationException("duplicate message");
        when(processedMessageRepository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> stockService.createFor(UUID.randomUUID(), 1L)).isSameAs(failure);
        verifyNoInteractions(stockRepository, outboxWriter);
    }

    @Test
    @DisplayName("재고 저장 실패는 이력과 함께 롤백되도록 예외를 전파한다")
    void createFor_shouldPropagate_whenStockSaveFails() {
        var failure = new DataIntegrityViolationException("stock insert failed");
        when(stockRepository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> stockService.createFor(UUID.randomUUID(), 1L)).isSameAs(failure);
        verify(processedMessageRepository).save(any());
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("Outbox 기록 실패는 재고와 처리 이력이 함께 롤백되도록 전파한다")
    void createFor_shouldPropagate_whenOutboxWriteFails() {
        when(stockRepository.save(any(Stock.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var failure = new DataIntegrityViolationException("outbox insert failed");
        when(outboxWriter.write("stock.events", "1", new StockCreated(1L, 0))).thenThrow(failure);

        assertThatThrownBy(() -> stockService.createFor(UUID.randomUUID(), 1L)).isSameAs(failure);

        verify(processedMessageRepository).save(any());
        verify(stockRepository).save(any());
    }
}
