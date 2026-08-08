package com.eda.stock.application.service;


import com.eda.stock.application.port.in.CreateStockCommand;
import com.eda.stock.application.port.in.CreateStockUseCase;
import com.eda.stock.application.port.in.HandleCreateProductCommand;
import com.eda.stock.application.port.out.InboxEventPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductCreatedEventHandlerTest {

    @Mock
    private InboxEventPort inboxEventPort;

    @Mock
    private CreateStockUseCase createStockUseCase;

    @InjectMocks
    private ProductCreatedEventHandler handler;

    @Test
    @DisplayName("처음 수신한 이벤면 Inbox에 저장하고 재고를 생성한다.")
    void handleNewEvent() {
        // given
        UUID eventId = UUID.randomUUID();
        HandleCreateProductCommand command = new HandleCreateProductCommand(
                eventId,
                1L
        );

        when(inboxEventPort.existByEventId(eventId))
                .thenReturn(false);

        // when
        handler.handle(command);

        // then
        InOrder order = inOrder(
                inboxEventPort,
                createStockUseCase
        );

        order.verify(inboxEventPort)
                .save(eventId, "ProductCreatedEvent");

        order.verify(createStockUseCase)
                .create(new CreateStockCommand(1L));
    }

    @Test
    @DisplayName("이미 처리된 이벤트면 재고를 다시 생성하지 않는다.")
    void ignoreDuplicatedEvent() {
        // given
        UUID eventId = UUID.randomUUID();
        HandleCreateProductCommand command = new HandleCreateProductCommand(
                eventId,
                1L
        );

        when(inboxEventPort.existByEventId(eventId))
                .thenReturn(true);

        // when
        handler.handle(command);

        // then
        verify(inboxEventPort, never())
                .save(any(), any());

        verifyNoInteractions(createStockUseCase);

    }

}
