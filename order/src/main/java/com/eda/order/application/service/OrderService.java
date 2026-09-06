package com.eda.order.application.service;

import com.eda.common.event.OrderCreatedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.CreateOrderCommand;
import com.eda.order.application.port.in.CreateOrderUseCase;
import com.eda.order.application.port.in.GetOrderUseCase;
import com.eda.order.application.port.out.LoadOrderPort;
import com.eda.order.application.port.out.LoadProductInfoPort;
import com.eda.order.application.port.out.SaveOrderCreatedOutboxPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.Order;
import com.eda.order.domain.ProductInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService implements CreateOrderUseCase, GetOrderUseCase {

    private final LoadProductInfoPort loadProductInfoPort;
    private final SaveOrderCreatedOutboxPort saveOrderCreatedOutboxPort;
    private final SaveOrderPort saveOrderPort;
    private final LoadOrderPort loadOrderPort;

    @Override
    @Transactional
    public Order create(CreateOrderCommand command) {
        List<Long> productIds = command.lines().stream()
                .map(CreateOrderCommand.Line::productId)
                .toList();

        List<ProductInfo> productInfos =
                loadProductInfoPort.loadAllByProductIds(productIds);

        Map<Long, ProductInfo> productInfoById =
                productInfos.stream()
                        .collect(Collectors.toMap(
                                ProductInfo::getProductId,
                                Function.identity()
                        ));

        List<Order.LineItem> lineItems = command.lines().stream()
                .map(line -> {
                    ProductInfo productInfo =
                            productInfoById.get(line.productId());

                    if (productInfo == null) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
                    }

                    return new Order.LineItem(
                            line.productId(),
                            productInfo.getName(),
                            productInfo.getPrice(),
                            line.quantity()
                    );
                })
                .toList();

        Order order = Order.create(
                command.memberId(),
                lineItems
        );

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
                eventLines,
                Instant.now()
        );
        saveOrderCreatedOutboxPort.save(event);
        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public Order get(Long orderId) {

        return loadOrderPort
                .loadById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
