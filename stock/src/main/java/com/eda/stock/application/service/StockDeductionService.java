package com.eda.stock.application.service;

import com.eda.common.event.StockDeductedEvent;
import com.eda.common.event.StockDeductionFailedEvent;
import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.stock.application.port.in.DeductStockCommand;
import com.eda.stock.application.port.in.DeductStockUseCase;
import com.eda.stock.application.port.out.FindStockPort;
import com.eda.stock.application.port.out.InboxEventPort;
import com.eda.stock.application.port.out.OutboxEventPort;
import com.eda.stock.application.port.out.SaveStockPort;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.inbox.InboxEvent;
import com.eda.stock.domain.outbox.OutboxEvent;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StockDeductionService implements DeductStockUseCase {

    private static final String EVENT_TYPE = "ORDER_CREATED";

    private final InboxEventPort inboxEventPort;
    private final FindStockPort findStockPort;
    private final SaveStockPort saveStockPort;
    private final OutboxEventPort outboxEventPort;
    private final ObjectMapper objectMapper;

    @Override
    public void deduct(DeductStockCommand command) {
        validate(command);

        String aggregateId = command.orderId().toString();

        // 이미 처리한 이벤트
        if (inboxEventPort.exists(
                command.eventId(),
                EVENT_TYPE,
                aggregateId
        )) {
            return;
        }

        // 처음 받은 이벤트 DB에 기록
        InboxEvent inboxEvent = InboxEvent.processed(
                command.eventId(),
                EVENT_TYPE,
                aggregateId,
                LocalDateTime.now()
        );

        inboxEventPort.save(inboxEvent);

        Map<Long, Integer> quantityByProductId = command.lines().stream()
                .collect(Collectors.toMap(
                        DeductStockCommand.LineCommand::productId,
                        DeductStockCommand.LineCommand::quantity
                ));

        List<Stock> stocks = findStockPort.findAllByProductIdIn(quantityByProductId.keySet());

        // product.events 처리가 아직 안 돼 Stock 자체가 없다면 재시도하도록 예외 발생
        if (stocks.size() != quantityByProductId.size()) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
        }

        // 실제 차감 전에 전부 확인(재고 확인)
        boolean canDeductAll = stocks.stream()
                .allMatch(stock ->
                        stock.canDeduct(
                                quantityByProductId.get(stock.getProductId())
                        )
                );

        if (!canDeductAll) {
            saveDeductionFailedEvent(command.orderId());
            return;
        }

        // 모든 상품의 재고가 충분할 때만 실제 차감
        stocks.forEach(stock ->
                stock.deduct(
                        quantityByProductId.get(stock.getProductId())
                )
        );

        saveStockPort.saveAll(stocks);
        saveDeductedEvent(command.orderId());
    }

    private void validate(DeductStockCommand command) {
        if (command == null
                || command.eventId() == null
                || command.orderId() == null
                || command.orderId() <= 0
                || command.lines() == null
                || command.lines().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        boolean hasInvalidLine = command.lines().stream()
                .anyMatch(line ->
                        line == null
                                || line.productId() == null
                                || line.productId() <= 0
                                || line.quantity() <= 0
                );

        if (hasInvalidLine) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        long productCount = command.lines().stream()
                .map(DeductStockCommand.LineCommand::productId)
                .distinct()
                .count();

        if (productCount != command.lines().size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void saveDeductedEvent(Long orderId) {
        StockDeductedEvent event = new StockDeductedEvent(
                UUID.randomUUID(),
                orderId
        );

        saveOutbox(
                orderId,
                "STOCK_DEDUCTED",
                event
        );
    }

    private void saveDeductionFailedEvent(Long orderId) {
        StockDeductionFailedEvent event =
                new StockDeductionFailedEvent(
                        UUID.randomUUID(),
                        orderId,
                        StockDeductionFailedEvent.Reason.INSUFFICIENT_STOCK
                );

        saveOutbox(
                orderId,
                "STOCK_DEDUCTION_FAILED",
                event
        );
    }

    private void saveOutbox(
            Long orderId,
            String eventType,
            Object event
    ) {
        OutboxEvent outboxEvent = OutboxEvent.pending(
                "ORDER",
                orderId.toString(),
                eventType,
                serialize(event)
        );

        outboxEventPort.save(outboxEvent);
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
