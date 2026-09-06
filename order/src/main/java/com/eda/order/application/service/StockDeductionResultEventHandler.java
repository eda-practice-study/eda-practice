package com.eda.order.application.service;

import com.eda.common.event.StockDeductionResultEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.order.application.port.in.HandleStockDeductionResultCommand;
import com.eda.order.application.port.in.HandleStockDeductionResultUseCase;
import com.eda.order.application.port.out.InboxEventPort;
import com.eda.order.application.port.out.LoadOrderPort;
import com.eda.order.application.port.out.SaveOrderPort;
import com.eda.order.domain.CancelReason;
import com.eda.order.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockDeductionResultEventHandler implements HandleStockDeductionResultUseCase {

    private static final String EVENT_TYPE = "StockDeductionResultEvent";

    private final InboxEventPort inboxEventPort;
    private final LoadOrderPort loadOrderPort;
    private final SaveOrderPort saveOrderPort;

    @Override
    @Transactional
    public void handle(HandleStockDeductionResultCommand command) {
        if (inboxEventPort.existsByEventId(command.eventId())) return;

        Order order = loadOrderPort
                .loadById(command.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (command.result() == StockDeductionResultEvent.Result.DEDUCTED) {
            order.markStockReserved();
        } else {
            order.cancel(CancelReason.INSUFFICIENT_STOCK);
        }

        saveOrderPort.save(order);
        inboxEventPort.save(command.eventId(), EVENT_TYPE);
    }
}
