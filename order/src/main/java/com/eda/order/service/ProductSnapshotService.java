package com.eda.order.service;

import com.eda.common.message.order.ProductSnapshotCreated;
import com.eda.common.message.product.ProductCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.domain.ProcessedMessage;
import com.eda.order.domain.ProcessedMessageRepository;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.domain.ProductSnapshotRepository;
import com.eda.order.exception.InvalidProductEventException;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductSnapshotService {

    private static final String ORDER_EVENTS_TOPIC = "order.events";

    private final ProductSnapshotRepository productSnapshotRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public boolean createFor(UUID messageId, ProductCreated event) {
        validateRequest(messageId, event);

        if (processedMessageRepository.existsById(messageId)) {
            return false;
        }
        processedMessageRepository.save(new ProcessedMessage(messageId));

        if (productSnapshotRepository.existsByProductId(event.productId())) {
            return false;
        }
        ProductSnapshot snapshot = productSnapshotRepository.save(
                ProductSnapshot.of(event.productId(), event.name(), BigDecimal.valueOf(event.price())));

        outboxWriter.write(
                ORDER_EVENTS_TOPIC,
                snapshot.getProductId().toString(),
                new ProductSnapshotCreated(snapshot.getProductId(), snapshot.getName(), snapshot.getPrice().longValueExact()));

        return true;
    }

    private void validateRequest(UUID messageId, ProductCreated event) {
        if (messageId == null || event == null || event.productId() == null || event.productId() <= 0
                || event.name() == null || event.name().isBlank() || event.price() <= 0) {
            throw new InvalidProductEventException("messageId와 양수인 productId·가격 및 상품명이 필요합니다");
        }
    }
}
