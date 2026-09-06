package com.eda.product.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.eda.common.event.ProductCreatedEvent;
import com.eda.product.application.port.out.LoadPendingOutboxPort;
import com.eda.product.application.port.out.MarkOutboxPublishedPort;
import com.eda.product.application.port.out.PublishProductEventPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    private LoadPendingOutboxPort loadPendingOutboxPort;

    @Mock
    private PublishProductEventPort publishProductEventPort;

    @Mock
    private MarkOutboxPublishedPort markOutboxPublishedPort;

    @InjectMocks
    private OutboxRelayService outboxRelayService;

    @Test
    @DisplayName("PENDING 이벤트를 Kafka로 발행한 뒤 PUBLISHED 상태로 변경한다")
    void relayPendingEvent() {
        // given
        ProductCreatedEvent event = new ProductCreatedEvent(
                UUID.randomUUID(),
                1L,
                "티셔츠",
                BigDecimal.valueOf(1000),
                Instant.now()
        );

        when(loadPendingOutboxPort.loadPending())
                .thenReturn(List.of(event));

        // when
        outboxRelayService.relay();

        // then
        InOrder order = inOrder(
                publishProductEventPort,
                markOutboxPublishedPort
        );

        order.verify(publishProductEventPort)
                .publish(event);

        order.verify(markOutboxPublishedPort)
                .markPublished(event.eventId());
    }

    @Test
    @DisplayName("PENDING 이벤트가 없으면 아무것도 발행하지 않는다")
    void doNothingWhenPendingEventDoesNotExist() {
        // given
        when(loadPendingOutboxPort.loadPending())
                .thenReturn(List.of());

        // when
        outboxRelayService.relay();

        // then
        verifyNoInteractions(
                publishProductEventPort,
                markOutboxPublishedPort
        );
    }

    @Test
    @DisplayName("Kafka 발행에 실패하면 PUBLISHED 상태로 변경하지 않는다")
    void doNotMarkPublishedWhenPublishFails() {
        // given
        ProductCreatedEvent event = new ProductCreatedEvent(
                UUID.randomUUID(),
                1L,
                "티셔츠",
                BigDecimal.valueOf(1000),
                Instant.now()
        );

        when(loadPendingOutboxPort.loadPending())
                .thenReturn(List.of(event));

        doThrow(new RuntimeException("Kafka publish failed"))
                .when(publishProductEventPort)
                .publish(event);

        // when & then
        assertThatThrownBy(() -> outboxRelayService.relay())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Kafka publish failed");

        // markPublished 함수가 호출되지 않았음을 검증
        verify(markOutboxPublishedPort, never())
                .markPublished(any());
    }
}