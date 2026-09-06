package com.eda.stock.application.service;

import com.eda.common.event.StockDeductionResultEvent;
import com.eda.stock.application.port.out.LoadPendingStockResultOutboxPort;
import com.eda.stock.application.port.out.MarkStockResultOutboxPublishedPort;
import com.eda.stock.application.port.out.PublishStockResultEventPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockResultOutboxRelayServiceTest {

    @Mock
    private LoadPendingStockResultOutboxPort loadPendingOutboxPort;

    @Mock
    private PublishStockResultEventPort publishStockResultEventPort;

    @Mock
    private MarkStockResultOutboxPublishedPort markPublishedPort;

    @InjectMocks
    private StockResultOutboxRelayService relayService;

    @Test
    @DisplayName("재고 차감 결과를 발행한 뒤 PUBLISHED로 변경한다")
    void relayPendingEvent() {
        // given
        StockDeductionResultEvent event =
                new StockDeductionResultEvent(
                        UUID.randomUUID(),
                        1L,
                        StockDeductionResultEvent.Result.DEDUCTED,
                        Instant.now()
                );

        when(loadPendingOutboxPort.loadPending())
                .thenReturn(List.of(event));

        // when
        relayService.relay();

        // then
        InOrder order = inOrder(
                publishStockResultEventPort,
                markPublishedPort
        );

        order.verify(publishStockResultEventPort)
                .publish(event);

        order.verify(markPublishedPort)
                .markPublished(event.eventId());
    }

    @Test
    @DisplayName("Kafka 발행에 실패하면 PUBLISHED로 변경하지 않는다")
    void doNotMarkPublishedWhenPublishFails() {
        // given
        StockDeductionResultEvent event =
                new StockDeductionResultEvent(
                        UUID.randomUUID(),
                        1L,
                        StockDeductionResultEvent.Result.DEDUCTED,
                        Instant.now()
                );

        when(loadPendingOutboxPort.loadPending())
                .thenReturn(List.of(event));

        doThrow(new RuntimeException("Kafka publish failed"))
                .when(publishStockResultEventPort)
                .publish(event);

        // when & then
        assertThatThrownBy(relayService::relay)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Kafka publish failed");

        verify(markPublishedPort, never())
                .markPublished(any());
    }
}