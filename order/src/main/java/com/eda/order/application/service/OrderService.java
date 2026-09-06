package com.eda.order.application.service;

import com.eda.common.event.AggregateType;
import com.eda.common.event.EventTypes;
import com.eda.common.event.OrderCreated;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.CreateOrderUseCase;
import com.eda.order.application.port.in.GetOrderUseCase;
import com.eda.order.application.port.out.LoadOrderPort;
import com.eda.order.application.port.out.LoadProductInfoPort;
import com.eda.order.application.port.out.SaveOrderOutboxEventPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderLine;
import com.eda.order.domain.OutboxEvent;
import com.eda.order.domain.ProductInfo;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OrderService implements CreateOrderUseCase, GetOrderUseCase {

    private final LoadProductInfoPort loadProductInfoPort;
    private final SaveOrderPort saveOrderPort;
    private final LoadOrderPort loadOrderPort;
    private final SaveOrderOutboxEventPort saveOrderOutboxEventPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public CreateOrderUseCase.OrderResult create(Long memberId, List<LineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        List<Order.LineItem> items = lines.stream()
                .map(line -> toLineItem(line.productId(), line.quantity()))
                .toList();
        Order order = saveOrderPort.save(Order.create(memberId, items));

        List<OrderCreated.Line> eventLines = order.getOrderLines().stream()
                .map(line -> new OrderCreated.Line(line.getProductId(), line.getQuantity()))
                .toList();
        OrderCreated event = new OrderCreated(UUID.randomUUID().toString(), order.getId(), eventLines);
        String payload = objectMapper.writeValueAsString(event);

        saveOrderOutboxEventPort
                .save(OutboxEvent.create(AggregateType.ORDER, order.getId(), EventTypes.OrderCreated, payload));
        return new CreateOrderUseCase.OrderResult(order.getId(), order.getStatus(), order.getTotalAmount());
    }

    @Override
    @Transactional(readOnly = true)
    public GetOrderUseCase.OrderResult get(Long orderId) {
        Order order = loadOrderPort.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        return new GetOrderUseCase.OrderResult(
                order.getId(), order.getStatus(), order.getTotalAmount(), order.getCancelReason()
        );
    }

    private Order.LineItem toLineItem(Long productId, int quantity) {
        ProductInfo productInfo = loadProductInfoPort.findByProductId(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return new Order.LineItem(productId, productInfo.getName(), productInfo.getPrice(), quantity);
    }
}
