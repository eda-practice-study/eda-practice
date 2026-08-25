package com.eda.order.application.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.CreateOrderCommand;
import com.eda.order.application.port.in.CreateOrderResult;
import com.eda.order.application.port.in.CreateOrderUseCase;
import com.eda.order.application.port.out.FindOrderProductPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderProduct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderCommandService implements CreateOrderUseCase {

    private final FindOrderProductPort findOrderProductPort;
    private final SaveOrderPort saveOrderPort;

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

        return new CreateOrderResult(
                savedOrder.getId(),
                savedOrder.getStatus(),
                savedOrder.getTotalAmount()
        );
    }
}
