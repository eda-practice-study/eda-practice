package com.eda.stock.application.service;

import com.eda.common.event.StockDeductionResultEvent;
import com.eda.stock.application.port.in.HandleOrderCreatedCommand;
import com.eda.stock.application.port.in.HandleOrderCreatedUseCase;
import com.eda.stock.application.port.out.InboxEventPort;
import com.eda.stock.application.port.out.LoadStockForUpdatePort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.application.port.out.SaveStockResultOutboxPort;
import com.eda.stock.domain.Stock;
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
public class OrderCreatedEventHandler implements HandleOrderCreatedUseCase {

    private static final String EVENT_TYPE = "OrderCreatedEvent";
    private final InboxEventPort inboxEventPort;
    private final LoadStockForUpdatePort loadStockForUpdatePort;
    private final SaveStockPort saveStockPort;
    private final SaveStockResultOutboxPort saveStockResultOutboxPort;


    @Override
    @Transactional
    public void handle(HandleOrderCreatedCommand command) {
        if (inboxEventPort.existByEventId(command.eventId())) {
            return;
        }

        inboxEventPort.save(command.eventId(), EVENT_TYPE);

        List<Long> productIds = command.lines().stream()
                .map(HandleOrderCreatedCommand.Line::productId)
                .sorted()
                .toList();

        List<Stock> stocks = loadStockForUpdatePort.loadAllByProductIdsForUpdate(productIds);

        Map<Long, Stock> stockByProductId = stocks.stream()
                .collect(Collectors.toMap(
                        Stock::getProductId,
                        Function.identity()
                ));

        boolean canDeductAll =
                stocks.size() == productIds.size()
                && command.lines().stream()
                        .allMatch(line -> {
                            Stock stock = stockByProductId.get(line.productId());

                            return stock != null
                                    && stock.canDeduct(line.quantity());
                        });
        StockDeductionResultEvent.Result result;

        if (canDeductAll) {
            command.lines().forEach(line -> {
                Stock stock = stockByProductId.get(line.productId());
                stock.deduct(line.quantity());
            });

            stocks.forEach(saveStockPort::save);

            result = StockDeductionResultEvent.Result.DEDUCTED;
        } else {
            result = StockDeductionResultEvent.Result.INSUFFICIENT_STOCK;
        }

        StockDeductionResultEvent resultEvent =
                new StockDeductionResultEvent(
                        UUID.randomUUID(),
                        command.orderId(),
                        result,
                        Instant.now()
                );

        saveStockResultOutboxPort.save(resultEvent);
    }
}
