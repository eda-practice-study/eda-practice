package com.eda.stock.application.service;

import com.eda.stock.application.port.in.CreateStockCommand;
import com.eda.stock.application.port.in.CreateStockUseCase;
import com.eda.stock.application.port.in.HandleCreateProductCommand;
import com.eda.stock.application.port.in.HandleProductCreatedUseCase;
import com.eda.stock.application.port.out.InboxEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ProductCreatedEventHandler implements HandleProductCreatedUseCase {

    private static final String EVENT_TYPE = "ProductCreatedEvent";

    private final InboxEventPort inboxEventPort;
    private final CreateStockUseCase createStockUseCase;

    @Override
    public void handle(HandleCreateProductCommand command) {
        if (inboxEventPort.existByEventId(command.eventId())) return ;

        inboxEventPort.save(
                command.eventId(),
                EVENT_TYPE
        );

        createStockUseCase.create(new CreateStockCommand(command.productId()));


    }
}
