package com.eda.order.application.service;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.CreateOrderCommand;
import com.eda.order.application.port.in.CreateOrderResult;
import com.eda.order.application.port.in.CreateOrderUseCase;
import com.eda.order.application.port.out.FindOrderProductPort;
import com.eda.order.application.port.out.OutboxEventPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderProduct;
import com.eda.order.domain.outbox.OutboxEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderCommandService implements CreateOrderUseCase {

    private final FindOrderProductPort findOrderProductPort;
    private final SaveOrderPort saveOrderPort;
    private final OutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

    @Override
    public CreateOrderResult create(CreateOrderCommand command) {
        if (command == null
                || command.memberId() == null
                || command.lines() == null
                || command.lines().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        List<Long> productIds = command.lines().stream()
                .map(CreateOrderCommand.LineCommand::productId)
                .distinct()
                .toList();

        List<OrderProduct> products = findOrderProductPort.findAllByProductIdIn(productIds);

        Map<Long, OrderProduct> productMap = products.stream()
                .collect(Collectors.toMap(OrderProduct::getProductId, Function.identity()));

        List<Order.LineItem> items = command.lines().stream()
                .map(line -> {
                    OrderProduct product = productMap.get(line.productId());

                    if (product == null) {
                        throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                    }

                    return new Order.LineItem(
                            product.getProductId(),
                            product.getName(),
                            product.getPrice(),
                            line.quantity()
                    );
                })
                .toList();

        Order order = Order.create(command.memberId(), items);
        Order savedOrder = saveOrderPort.save(order);

        List<OrderCreatedEvent.Line> eventLines =
                savedOrder.getOrderLines().stream()
                        .map(line -> new OrderCreatedEvent.Line(
                                line.getProductId(),
                                line.getQuantity()
                        ))
                        .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                savedOrder.getId(),
                eventLines
        );

        String payload = serialize(event);

        OutboxEvent outboxEvent = OutboxEvent.pending(
                "ORDER",
                savedOrder.getId().toString(),
                "ORDER_CREATED",
                payload
        );

        outboxEventPort.save(outboxEvent);

        return new CreateOrderResult(
                savedOrder.getId(),
                savedOrder.getStatus(),
                savedOrder.getTotalAmount()
        );
    }

    private String serialize(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
