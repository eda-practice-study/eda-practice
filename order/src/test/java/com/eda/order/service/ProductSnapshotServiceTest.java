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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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
    @DisplayName("이력과 상품명·가격을 복사한 스냅샷 및 생성 이벤트를 순서대로 저장한다")
    void create() {
        UUID messageId = UUID.randomUUID();
        var event = new ProductCreated(1L, "키보드", 30000);
        when(productSnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        boolean created = service.createFor(messageId, event);

        var history = ArgumentCaptor.forClass(ProcessedMessage.class);
        var snapshot = ArgumentCaptor.forClass(ProductSnapshot.class);
        var order = inOrder(processedMessageRepository, productSnapshotRepository, outboxWriter);
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
    void duplicateMessage() {
        UUID messageId = UUID.randomUUID();
        when(processedMessageRepository.existsById(messageId)).thenReturn(true);

        assertThat(service.createFor(messageId, new ProductCreated(1L, "키보드", 30000))).isFalse();

        verify(processedMessageRepository).existsById(messageId);
        verifyNoMoreInteractions(processedMessageRepository);
        verifyNoInteractions(productSnapshotRepository, outboxWriter);
    }

    @Test
    @DisplayName("다른 메시지라도 기존 상품 스냅샷은 덮어쓰지 않고 이력만 기록한다")
    void existingSnapshot() {
        UUID messageId = UUID.randomUUID();
        when(productSnapshotRepository.existsByProductId(1L)).thenReturn(true);

        assertThat(service.createFor(messageId, new ProductCreated(1L, "바뀐 이름", 50000))).isFalse();

        verify(processedMessageRepository).save(any(ProcessedMessage.class));
        verify(productSnapshotRepository, never()).save(any());
        verifyNoInteractions(outboxWriter);
    }

    @ParameterizedTest
    @MethodSource("invalidEvents")
    @DisplayName("본문이나 상품 식별자·상품명·가격이 잘못되면 저장하지 않는다")
    void invalidEvent(ProductCreated event) {
        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), event))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(productSnapshotRepository, processedMessageRepository, outboxWriter);
    }

    static Stream<ProductCreated> invalidEvents() {
        return Stream.of(null,
                new ProductCreated(null, "키보드", 30000),
                new ProductCreated(0L, "키보드", 30000),
                new ProductCreated(-1L, "키보드", 30000),
                new ProductCreated(1L, null, 30000),
                new ProductCreated(1L, " ", 30000),
                new ProductCreated(1L, "키보드", 0),
                new ProductCreated(1L, "키보드", -1));
    }

    @Test
    @DisplayName("메시지 ID가 없으면 저장하지 않는다")
    void missingMessageId() {
        assertThatThrownBy(() -> service.createFor(null, new ProductCreated(1L, "키보드", 30000)))
                .isInstanceOf(InvalidProductEventException.class);
        verifyNoInteractions(productSnapshotRepository, processedMessageRepository, outboxWriter);
    }

    @Test
    @DisplayName("처리 이력 저장 실패는 전파하고 스냅샷과 Outbox를 저장하지 않는다")
    void historyFailure() {
        var failure = new DataIntegrityViolationException("duplicate message");
        when(processedMessageRepository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), new ProductCreated(1L, "키보드", 30000)))
                .isSameAs(failure);
        verifyNoInteractions(productSnapshotRepository, outboxWriter);
    }

    @Test
    @DisplayName("스냅샷 저장 실패는 전파하고 Outbox를 기록하지 않는다")
    void snapshotFailure() {
        var failure = new DataIntegrityViolationException("snapshot insert failed");
        when(productSnapshotRepository.save(any())).thenThrow(failure);

        assertThatThrownBy(() -> service.createFor(UUID.randomUUID(), new ProductCreated(1L, "키보드", 30000)))
                .isSameAs(failure);
        verify(processedMessageRepository).save(any());
        verifyNoInteractions(outboxWriter);
    }

    @Test
    @DisplayName("Outbox 기록 실패는 트랜잭션이 롤백되도록 전파한다")
    void outboxFailure() {
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
