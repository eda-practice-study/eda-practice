package com.eda.stock.application.service;

import com.eda.common.event.StockDeductionResultEvent;
import com.eda.stock.application.port.in.RelayStockResultEventUseCase;
import com.eda.stock.application.port.out.LoadPendingStockResultOutboxPort;
import com.eda.stock.application.port.out.MarkStockResultOutboxPublishedPort;
import com.eda.stock.application.port.out.PublishStockResultEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockResultOutboxRelayService
    implements RelayStockResultEventUseCase {

    private final LoadPendingStockResultOutboxPort loadPendingStockResultOutboxPort;
    private final PublishStockResultEventPort publishStockResultEventPort;
    private final MarkStockResultOutboxPublishedPort markStockResultOutboxPublishedPort;

    @Override
    @Transactional
    public void relay() {
        List<StockDeductionResultEvent> events =
                loadPendingStockResultOutboxPort.loadPending();

        for (StockDeductionResultEvent event : events) {
            publishStockResultEventPort.publish(event);
            markStockResultOutboxPublishedPort.markPublished(event.eventId());
        }
    }
}
