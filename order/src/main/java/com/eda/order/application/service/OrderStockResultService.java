package com.eda.order.application.service;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.HandleStockResultUseCase;
import com.eda.order.application.port.out.FindOrderPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderCancelReason;
import com.eda.order.domain.OrderStatus;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderStockResultService implements HandleStockResultUseCase {

    private final FindOrderPort findOrderPort;
    private final SaveOrderPort saveOrderPort;

    @Override
    public void handleDeducted(Long orderId) {
        Order order = findOrder(orderId);

        // 같은 성공 이벤트가 다시 온 경우
        if (order.getStatus() == OrderStatus.STOCK_RESERVED) {
            return;
        }

        order.markStockReserved();
        saveOrderPort.save(order);
    }

    @Override
    public void handleDeductionFailed(Long orderId) {
        Order order = findOrder(orderId);

        // 같은 실패 이벤트가 다시 온 경우
        if (order.getStatus() == OrderStatus.CANCELED
                && order.getCancelReason()
                == OrderCancelReason.INSUFFICIENT_STOCK) {
            return;
        }

        // 재고 성공 후 뒤늦게 실패 이벤트가 오는 충돌 방지
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        order.cancel(OrderCancelReason.INSUFFICIENT_STOCK);
        saveOrderPort.save(order);
    }

    private Order findOrder(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        return findOrderPort.findById(orderId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.ORDER_NOT_FOUND)
                );
    }
}
