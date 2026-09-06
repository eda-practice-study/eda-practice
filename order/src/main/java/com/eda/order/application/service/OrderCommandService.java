package com.eda.order.application.service;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.dto.CreateOrderResponse;
import com.eda.order.application.port.in.CreateOrderUseCase;
import com.eda.order.application.port.in.command.CreateOrderCommand;
import com.eda.order.application.port.out.LoadProductCatalogPort;
import com.eda.order.application.port.out.SaveOrderOutboxEventPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.Order;
import com.eda.order.domain.catalog.ProductCatalogItem;
import com.eda.order.domain.outbox.AggregateType;
import com.eda.order.domain.outbox.EventType;
import com.eda.order.domain.outbox.OrderOutboxEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderCommandService implements CreateOrderUseCase {

    private final LoadProductCatalogPort loadProductCatalogPort;
    private final SaveOrderPort saveOrderPort;
    private final SaveOrderOutboxEventPort saveOrderOutboxEventPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CreateOrderResponse create(CreateOrderCommand command) {
        List<Long> productIds = command.lines().stream()
                .map(CreateOrderCommand.Line::productId)
                .distinct()
                .toList();

        Map<Long, ProductCatalogItem> productById =
                loadProductCatalogPort
                        .loadAllByProductIds(productIds)
                        .stream()
                        .collect(Collectors.toMap(
                                ProductCatalogItem::getProductId,
                                Function.identity()
                        ));

        if (productById.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        List<Order.LineItem> lineItems = command.lines().stream()
                .map(line -> {
                    ProductCatalogItem product =
                            productById.get(line.productId());

                    return new Order.LineItem(
                            line.productId(),
                            product.getProductName(),
                            product.getUnitPrice(),
                            line.quantity()
                    );
                })
                .toList();

        Order order = Order.create(
                command.memberId(),
                lineItems
        );

        Order savedOrder = saveOrderPort.save(order);

        UUID eventId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                eventId,
                savedOrder.getId(),
                savedOrder.getOrderLines().stream()
                        .map(line -> new OrderCreatedEvent.Line(
                                line.getProductId(),
                                line.getQuantity()
                        ))
                        .toList()
        );

        String payload = objectMapper.writeValueAsString(event);

        OrderOutboxEvent outboxEvent = OrderOutboxEvent.create(
                eventId,
                AggregateType.ORDER,
                savedOrder.getId(),
                EventType.ORDER_CREATED,
                payload
        );

        saveOrderOutboxEventPort.save(outboxEvent);

        return CreateOrderResponse.from(savedOrder);
    }
}