package com.eda.stock.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @InjectMocks
    private StockService stockService;

    @Test
    @DisplayName("처리 이력을 저장한 뒤 수량이 0인 재고를 생성한다")
    void create() {
        UUID messageId = UUID.randomUUID();

        stockService.createFor(messageId, 1L);

        var history = ArgumentCaptor.forClass(ProcessedMessage.class);
        var stock = ArgumentCaptor.forClass(Stock.class);
        var order = inOrder(processedMessageRepository, stockRepository);
        order.verify(processedMessageRepository).existsById(messageId);
        order.verify(processedMessageRepository).saveAndFlush(history.capture());
        order.verify(stockRepository).existsByProductId(1L);
        order.verify(stockRepository).save(stock.capture());
        assertThat(history.getValue().getMessageId()).isEqualTo(messageId);
        assertThat(history.getValue().isNew()).isTrue();
        assertThat(stock.getValue().getProductId()).isEqualTo(1L);
        assertThat(stock.getValue().getQuantity()).isZero();
    }

    @Test
    @DisplayName("이미 처리한 메시지는 재고에 접근하지 않고 건너뛴다")
    void duplicateMessage() {
        UUID messageId = UUID.randomUUID();
        when(processedMessageRepository.existsById(messageId)).thenReturn(true);

        stockService.createFor(messageId, 1L);

        verify(processedMessageRepository).existsById(messageId);
        verifyNoMoreInteractions(processedMessageRepository);
        verifyNoInteractions(stockRepository);
    }

    @Test
    @DisplayName("새 메시지라도 이미 존재하는 상품 재고를 덮어쓰지 않는다")
    void existingStock() {
        UUID messageId = UUID.randomUUID();
        when(stockRepository.existsByProductId(1L)).thenReturn(true);

        stockService.createFor(messageId, 1L);

        verify(processedMessageRepository).saveAndFlush(any(ProcessedMessage.class));
        verify(stockRepository).existsByProductId(1L);
        verify(stockRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("상품 ID가 없거나 양수가 아니면 저장하지 않는다")
    void invalidProductId(Long productId) {
        assertThatThrownBy(() -> stockService.createFor(UUID.randomUUID(), productId))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(stockRepository, processedMessageRepository);
    }

    @Test
    @DisplayName("메시지 ID가 없으면 저장하지 않는다")
    void missingMessageId() {
        assertThatThrownBy(() -> stockService.createFor(null, 1L))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(stockRepository, processedMessageRepository);
    }

    @Test
    @DisplayName("이력 저장 중 동시 중복이 감지되면 재시도할 수 있도록 예외를 전파한다")
    void historyFailure() {
        var failure = new DataIntegrityViolationException("duplicate message");
        when(processedMessageRepository.saveAndFlush(any())).thenThrow(failure);

        assertThatThrownBy(() -> stockService.createFor(UUID.randomUUID(), 1L)).isSameAs(failure);
        verifyNoInteractions(stockRepository);
    }

    @Test
    @DisplayName("재고 저장 실패는 이력과 함께 롤백되도록 예외를 전파한다")
    void stockFailure() {
        var failure = new DataIntegrityViolationException("stock insert failed");
        when(stockRepository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> stockService.createFor(UUID.randomUUID(), 1L)).isSameAs(failure);
        verify(processedMessageRepository).saveAndFlush(any());
    }
}
