package com.eda.order.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.eda.common.message.order.ProductSnapshotCreated;
import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.domain.ProcessedMessage;
import com.eda.order.domain.ProcessedMessageRepository;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.domain.ProductSnapshotRepository;
import com.eda.order.exception.InvalidProductEventException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(MockitoExtension.class)
class ProductSnapshotServiceTest {

    @Mock
    private ProductSnapshotRepository productSnapshotRepository;
    @Mock
    private ProcessedMessageRepository processedMessageRepository;
    @Mock
    private OutboxWriter outboxWriter;
    @InjectMocks
    private ProductSnapshotService service;

    @Test
    @DisplayName("새 상품이면 스냅샷과 생성 이벤트를 저장한다")
    void createFor_shouldStoreEvent_whenProductNew() {
        UUID messageId = UUID.randomUUID();
        var event = new ProductCreated(1L, "키보드", 30000);
        when(productSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean created = service.createFor(messageId, event);

        ArgumentCaptor<ProcessedMessage> history = ArgumentCaptor.forClass(ProcessedMessage.class);
        ArgumentCaptor<ProductSnapshot> snapshot = ArgumentCaptor.forClass(ProductSnapshot.class);
        InOrder order = inOrder(processedMessageRepository, productSnapshotRepository, outboxWriter);
        order.verify(processedMessageRepository).existsById(messageId);
        order.verify(processedMessageRepository).save(history.capture());
        order.verify(productSnapshotRepository).existsByProductId(1L);
        order.verify(productSnapshotRepository).save(snapshot.capture());
        order.verify(outboxWriter).write("order.events", "1", new ProductSnapshotCreated(1L, "키보드", 30000));
        assertThat(created).isTrue();
        assertThat(history.getValue().getMessageId()).isEqualTo(messageId);
        assertThat(history.getValue().isNew()).isTrue();
        assertThat(snapshot.getValue().getProductId()).isEqualTo(1L);
        assertThat(snapshot.getValue().getName()).isEqualTo("키보드");
        assertThat(snapshot.getValue().getPrice()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("처리한 메시지는 스냅샷과 Outbox에 접근하지 않는다")
    void createFor_shouldSkip_whenMessageAlreadyProcessed() {
        UUID messageId = UUID.randomUUID();
        when(processedMessageRepository.existsById(messageId)).thenReturn(true);

        assertThat(service.createFor(messageId, new ProductCreated(1L, "키보드", 30000))).isFalse();

        verify(processedMessageRepository).existsById(messageId);
        verifyNoMoreInteractions(processedMessageRepository);
        verifyNoInteractions(productSnapshotRepository, outboxWriter);
    }

    @Test
    @DisplayName("다른 메시지라도 기존 상품 스냅샷은 덮어쓰지 않고 이력만 기록한다")
    void createFor_shouldPreserveSnapshot_whenProductAlreadyExists() {
        UUID messageId = UUID.randomUUID();
        when(productSnapshotRepository.existsByProductId(1L)).thenReturn(true);

        assertThat(service.createFor(messageId, new ProductCreated(1L, "바뀐 이름", 50000))).isFalse();

        verify(processedMessageRepository).save(any(ProcessedMessage.class));
        verify(productSnapshotRepository, never()).save(any());
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("상품 생성 이벤트 본문이 없으면 저장하지 않는다")
    void createFor_shouldReject_whenEventMissing() {
        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), null))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(productSnapshotRepository, processedMessageRepository, outboxWriter);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("상품 ID가 유효하지 않으면 저장하지 않는다")
    void createFor_shouldReject_whenProductIdInvalid(Long productId) {
        var event = new ProductCreated(productId, "키보드", 30000);

        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), event))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(productSnapshotRepository, processedMessageRepository, outboxWriter);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {" "})
    @DisplayName("상품명이 없거나 공백이면 저장하지 않는다")
    void createFor_shouldReject_whenNameBlank(String name) {
        var event = new ProductCreated(1L, name, 30000);

        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), event))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(productSnapshotRepository, processedMessageRepository, outboxWriter);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    @DisplayName("상품 가격이 0 이하이면 저장하지 않는다")
    void createFor_shouldReject_whenPriceNonPositive(long price) {
        var event = new ProductCreated(1L, "키보드", price);

        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), event))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(productSnapshotRepository, processedMessageRepository, outboxWriter);
    }


    @Test
    @DisplayName("메시지 ID가 없으면 저장하지 않는다")
    void createFor_shouldReject_whenMessageIdMissing() {
        assertThatThrownBy(() -> service.createFor(null, new ProductCreated(1L, "키보드", 30000)))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(productSnapshotRepository, processedMessageRepository, outboxWriter);
    }

    @Test
    @DisplayName("처리 이력 저장 실패는 전파하고 스냅샷과 Outbox를 저장하지 않는다")
    void createFor_shouldPropagate_whenHistorySaveFails() {
        var failure = new DataIntegrityViolationException("duplicate message");
        when(processedMessageRepository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), new ProductCreated(1L, "키보드", 30000)))
                .isSameAs(failure);
        verifyNoInteractions(productSnapshotRepository, outboxWriter);
    }

    @Test
    @DisplayName("스냅샷 저장 실패는 전파하고 Outbox를 기록하지 않는다")
    void createFor_shouldPropagate_whenSnapshotSaveFails() {
        var failure = new DataIntegrityViolationException("snapshot insert failed");
        when(productSnapshotRepository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), new ProductCreated(1L, "키보드", 30000)))
                .isSameAs(failure);
        verify(processedMessageRepository).save(any());
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("Outbox 기록 실패는 트랜잭션이 롤백되도록 전파한다")
    void createFor_shouldPropagate_whenOutboxWriteFails() {
        when(productSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var failure = new DataIntegrityViolationException("outbox insert failed");
        when(outboxWriter.write("order.events", "1", new ProductSnapshotCreated(1L, "키보드", 30000)))
                .thenThrow(failure);

        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), new ProductCreated(1L, "키보드", 30000)))
                .isSameAs(failure);
        verify(processedMessageRepository).save(any());
        verify(productSnapshotRepository).save(any());
    }
}
