package com.eda.order.application.service;

import com.eda.common.event.StockDeducted;
import com.eda.common.event.StockDeductionFailed;
import com.eda.common.event.StockDeductionResult;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.HandleStockResultUseCase;
import com.eda.order.application.port.out.LoadOrderPort;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStockResultService implements HandleStockResultUseCase {

    private final LoadOrderPort loadOrderPort;

    @Override
    @Transactional
    public void handle(StockDeductionResult event) {
        if (event == null || event.orderId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        Order order = loadOrderPort.findById(event.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (event instanceof StockDeducted) {
            handleSuccess(order);
            return;
        }

        if (event instanceof StockDeductionFailed failed) {
            handleFailure(order, failed.reason());
            return;
        }

        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }

    private void handleSuccess(Order order) {
        if (order.getStatus() == OrderStatus.CREATED) {
            order.markStockReserved();
            log.info("재고 예약 완료 orderId={}, status={}", order.getId(), order.getStatus());
            return;
        }

        if (order.getStatus() == OrderStatus.STOCK_RESERVED) {
            log.info("재고 예약 결과 중복 수신 무시 orderId={}", order.getId());
            return;
        }

        log.warn("현재 주문 상태에서 재고 성공 결과 무시 orderId={}, status={}", order.getId(), order.getStatus());
    }

    private void handleFailure(Order order, String reason) {
        if (order.getStatus() == OrderStatus.CREATED) {
            order.cancel(reason == null || reason.isBlank() ? "INSUFFICIENT_STOCK" : reason);
            log.info("재고 차감 실패로 주문 취소 orderId={}, reason={}", order.getId(), order.getCancelReason());
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELED) {
            log.info("주문 취소 결과 중복 수신 무시 orderId={}", order.getId());
            return;
        }

        log.warn("현재 주문 상태에서 재고 실패 결과 무시 orderId={}, status={}", order.getId(), order.getStatus());
    }
}
