package com.eda.stock.application.service;

import com.eda.common.event.AggregateType;
import com.eda.common.event.EventTypes;
import com.eda.common.event.OrderCreated;
import com.eda.common.event.StockDeducted;
import com.eda.common.event.StockDeductionFailed;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.in.DeductStockUseCase;
import com.eda.stock.application.port.out.LoadLockedStockPort;
import com.eda.stock.application.port.out.LoadStockOrderDeductionPort;
import com.eda.stock.application.port.out.SaveStockOrderDeductionPort;
import com.eda.stock.application.port.out.SaveStockOutboxEventPort;
import com.eda.stock.domain.OutboxEvent;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockOrderDeduction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockDeductionService implements DeductStockUseCase {

    private static final String STOCK_NOT_FOUND = "STOCK_NOT_FOUND";
    private static final String INVALID_ORDER = "INVALID_ORDER";

    private final LoadLockedStockPort loadLockedStockPort;
    private final LoadStockOrderDeductionPort loadDeductionPort;
    private final SaveStockOrderDeductionPort saveDeductionPort;
    private final SaveStockOutboxEventPort saveOutboxEventPort;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void deduct(OrderCreated event) {
        if (loadDeductionPort.findByOrderId(event.orderId()).isPresent()) {
            log.warn("주문 재고 차감 중복 수신 무시 orderId={}", event.orderId());
            return;
        }

        String resultEventId = UUID.randomUUID().toString();
        Map<Long, Integer> quantityByProductId = quantitiesByProductId(event.lines());
        if (quantityByProductId == null) {
            recordFailure(event.orderId(), resultEventId, INVALID_ORDER);
            return;
        }

        List<Stock> stocks = loadLockedStockPort.findAllByProductIdsForUpdate(quantityByProductId.keySet());
        if (stocks.size() != quantityByProductId.size()) {
            recordFailure(event.orderId(), resultEventId, STOCK_NOT_FOUND);
            return;
        }

        Map<Long, Stock> stockByProductId = new HashMap<>();
        for (Stock stock : stocks) {
            stockByProductId.put(stock.getProductId(), stock);
        }

        for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
            Stock stock = stockByProductId.get(entry.getKey());
            if (stock == null) {
                recordFailure(event.orderId(), resultEventId, STOCK_NOT_FOUND);
                return;
            }
            if (stock.getQuantity() < entry.getValue()) {
                recordFailure(event.orderId(), resultEventId, ErrorCode.INSUFFICIENT_STOCK.name());
                return;
            }
        }

        quantityByProductId.forEach((productId, quantity) -> stockByProductId.get(productId).deduct(quantity));
        saveDeductionPort.save(StockOrderDeduction.success(event.orderId(), resultEventId));
        saveResultEvent(
                new StockDeducted(resultEventId, event.orderId()),
                event.orderId(),
                EventTypes.StockDeducted
        );
        log.info("주문 재고 차감 성공 orderId={}", event.orderId());
    }

    private Map<Long, Integer> quantitiesByProductId(List<OrderCreated.Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }

        Map<Long, Integer> quantities = new HashMap<>();
        for (OrderCreated.Line line : lines) {
            if (line == null || line.productId() == null || line.quantity() <= 0
                    || quantities.containsKey(line.productId())) {
                return null;
            }
            quantities.put(line.productId(), line.quantity());
        }
        return quantities;
    }

    private void recordFailure(Long orderId, String eventId, String reason) {
        saveDeductionPort.save(StockOrderDeduction.failure(orderId, eventId, reason));
        saveResultEvent(
                new StockDeductionFailed(eventId, orderId, reason),
                orderId,
                EventTypes.StockDeductionFailed
        );
        log.info("주문 재고 차감 실패 orderId={}, reason={}", orderId, reason);
    }

    private void saveResultEvent(Object event, Long orderId, EventTypes eventType) {
        String payload = objectMapper.writeValueAsString(event);
        saveOutboxEventPort.save(OutboxEvent.create(AggregateType.STOCK, orderId, eventType, payload));
    }
}
