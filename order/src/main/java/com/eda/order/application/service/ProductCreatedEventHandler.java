package com.eda.order.application.service;

import com.eda.order.application.port.in.HandleProductCreatedCommand;
import com.eda.order.application.port.in.HandleProductCreatedUseCase;
import com.eda.order.application.port.out.InboxEventPort;
import com.eda.order.application.port.out.SaveProductInfoPort;
import com.eda.order.domain.ProductInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCreatedEventHandler implements HandleProductCreatedUseCase {

    private static final String EVENT_TYPE = "ProductCreatedEvent";

    private final InboxEventPort inboxEventPort;
    private final SaveProductInfoPort saveProductInfoPort;

    @Override
    @Transactional
    public void handle(HandleProductCreatedCommand command) {
        if (inboxEventPort.existsByEventId(command.eventId())) return;

        inboxEventPort.save(command.eventId(), EVENT_TYPE);

        ProductInfo productInfo = ProductInfo.create(
                command.productId(),
                command.name(),
                command.price()
        );

        saveProductInfoPort.save(productInfo);
    }
}
