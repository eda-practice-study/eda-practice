package com.eda.stock.application.service;

import com.eda.common.event.StockDeductionResultEvent;
import com.eda.stock.application.port.in.HandleOrderCreatedCommand;
import com.eda.stock.application.port.out.InboxEventPort;
import com.eda.stock.application.port.out.LoadStockForUpdatePort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.application.port.out.SaveStockResultOutboxPort;
import com.eda.stock.domain.Stock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCreatedEventHandlerTest {

    @Mock
    private InboxEventPort inboxEventPort;

    @Mock
    private LoadStockForUpdatePort loadStockForUpdatePort;

    @Mock
    private SaveStockPort saveStockPort;

    @Mock
    private SaveStockResultOutboxPort saveStockResultOutboxPort;

    @InjectMocks
    private OrderCreatedEventHandler handler;

    @Test
    @DisplayName("모든 상품의 재고가 충분하면 전체 재고를 차감한다")
    void deductAllStocks() {
        // given
        UUID eventId = UUID.randomUUID();

        HandleOrderCreatedCommand command =
                new HandleOrderCreatedCommand(
                        eventId,
                        100L,
                        List.of(
                                new HandleOrderCreatedCommand.Line(2L, 3),
                                new HandleOrderCreatedCommand.Line(1L, 2)
                        )
                );

        Stock stock1 = Stock.createFor(1L);
        stock1.add(5);

        Stock stock2 = Stock.createFor(2L);
        stock2.add(3);

        when(inboxEventPort.existByEventId(eventId))
                .thenReturn(false);

        when(loadStockForUpdatePort.loadAllByProductIdsForUpdate(
                List.of(1L, 2L)
        )).thenReturn(List.of(stock1, stock2));

        // when
        handler.handle(command);

        // then
        assertThat(stock1.getQuantity()).isEqualTo(3);
        assertThat(stock2.getQuantity()).isZero();

        verify(saveStockPort).save(stock1);
        verify(saveStockPort).save(stock2);

        verifyResultEvent(
                100L,
                StockDeductionResultEvent.Result.DEDUCTED
        );

        verify(inboxEventPort)
                .save(eventId, "OrderCreatedEvent");
    }

    @Test
    @DisplayName("상품 하나라도 재고가 부족하면 아무 재고도 차감하지 않는다")
    void doNotDeductWhenOneStockIsInsufficient() {
        // given
        UUID eventId = UUID.randomUUID();

        HandleOrderCreatedCommand command =
                new HandleOrderCreatedCommand(
                        eventId,
                        100L,
                        List.of(
                                new HandleOrderCreatedCommand.Line(1L, 2),
                                new HandleOrderCreatedCommand.Line(2L, 2)
                        )
                );

        Stock stock1 = Stock.createFor(1L);
        stock1.add(5);

        Stock stock2 = Stock.createFor(2L);
        stock2.add(1);

        when(inboxEventPort.existByEventId(eventId))
                .thenReturn(false);

        when(loadStockForUpdatePort.loadAllByProductIdsForUpdate(
                List.of(1L, 2L)
        )).thenReturn(List.of(stock1, stock2));

        // when
        handler.handle(command);

        // then
        assertThat(stock1.getQuantity()).isEqualTo(5);
        assertThat(stock2.getQuantity()).isEqualTo(1);

        verifyNoInteractions(saveStockPort);

        verifyResultEvent(
                100L,
                StockDeductionResultEvent.Result.INSUFFICIENT_STOCK
        );
    }

    @Test
    @DisplayName("주문 상품의 재고 행이 없으면 아무 재고도 차감하지 않는다")
    void doNotDeductWhenStockDoesNotExist() {
        // given
        UUID eventId = UUID.randomUUID();

        HandleOrderCreatedCommand command =
                new HandleOrderCreatedCommand(
                        eventId,
                        100L,
                        List.of(
                                new HandleOrderCreatedCommand.Line(1L, 2),
                                new HandleOrderCreatedCommand.Line(2L, 1)
                        )
                );

        Stock stock1 = Stock.createFor(1L);
        stock1.add(5);

        when(inboxEventPort.existByEventId(eventId))
                .thenReturn(false);

        when(loadStockForUpdatePort.loadAllByProductIdsForUpdate(
                List.of(1L, 2L)
        )).thenReturn(List.of(stock1));

        // when
        handler.handle(command);

        // then
        assertThat(stock1.getQuantity()).isEqualTo(5);

        verifyNoInteractions(saveStockPort);

        verifyResultEvent(
                100L,
                StockDeductionResultEvent.Result.INSUFFICIENT_STOCK
        );
    }

    @Test
    @DisplayName("이미 처리한 이벤트는 재고를 다시 차감하지 않는다")
    void ignoreDuplicatedEvent() {
        // given
        UUID eventId = UUID.randomUUID();

        HandleOrderCreatedCommand command =
                new HandleOrderCreatedCommand(
                        eventId,
                        100L,
                        List.of(
                                new HandleOrderCreatedCommand.Line(1L, 2)
                        )
                );

        when(inboxEventPort.existByEventId(eventId))
                .thenReturn(true);

        // when
        handler.handle(command);

        // then
        verify(inboxEventPort, never())
                .save(any(), any());

        verifyNoInteractions(
                loadStockForUpdatePort,
                saveStockPort,
                saveStockResultOutboxPort
        );
    }

    private void verifyResultEvent(
            Long orderId,
            StockDeductionResultEvent.Result expectedResult
    ) {
        ArgumentCaptor<StockDeductionResultEvent> captor =
                ArgumentCaptor.forClass(StockDeductionResultEvent.class);

        verify(saveStockResultOutboxPort)
                .save(captor.capture());

        StockDeductionResultEvent event = captor.getValue();

        assertThat(event.eventId()).isNotNull();
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.result()).isEqualTo(expectedResult);
        assertThat(event.occurredAt()).isNotNull();
    }
}