package com.eda.order.application.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.GetOrderResult;
import com.eda.order.application.port.in.GetOrderUseCase;
import com.eda.order.application.port.out.FindOrderPort;
import com.eda.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService implements GetOrderUseCase {

    private final FindOrderPort findOrderPort;

    @Override
    public GetOrderResult get(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Order order = findOrderPort.findById(orderId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.ORDER_NOT_FOUND)
                );

        List<GetOrderResult.Line> lines =
                order.getOrderLines().stream()
                        .map(line -> new GetOrderResult.Line(
                                line.getProductId(),
                                line.getProductName(),
                                line.getUnitPrice(),
                                line.getQuantity(),
                                line.getSubtotal()
                        ))
                        .toList();

        return new GetOrderResult(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCancelReason(),
                lines
        );
    }
}
