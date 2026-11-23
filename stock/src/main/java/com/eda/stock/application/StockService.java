package com.eda.stock.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.Topics;
import com.eda.common.message.stock.StockDecreased;
import com.eda.common.message.stock.StockIncreased;
import com.eda.common.message.stock.StockRegistered;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.config.StockReservationProperties;
import com.eda.stock.domain.ProcessedOrder;
import com.eda.stock.domain.Stock;
import com.eda.stock.infrastructure.ProcessedOrderRepository;
import com.eda.stock.infrastructure.StockRedisRepository;
import com.eda.stock.infrastructure.StockRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockRedisRepository stockRedisRepository;
    private final ProcessedOrderRepository processedOrderRepository;
    private final OutboxWriter outboxWriter;
    private final StockReservationProperties reservationProperties;

    @Transactional
    public Stock register(Long productId) {
        Optional<Stock> registered = stockRepository.findByProductId(productId);
        if (registered.isPresent()) {
            return registered.get();
        }

        Stock stock = stockRepository.save(Stock.createFor(productId));
        outboxWriter.write(Topics.STOCK_EVENTS, String.valueOf(productId),
                new StockRegistered(productId, stock.getQuantity()));

        return stock;
    }

    @Transactional
    public Stock increase(Long productId, int quantity) {
        Stock stock = stockRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        stock.increase(quantity);
        outboxWriter.write(Topics.STOCK_EVENTS, String.valueOf(productId),
                new StockIncreased(productId, quantity, stock.getQuantity()));

        return stock;
    }

    // TCC Try — Redis 가용 재고만 예약한다. 같은 reservationId 로 다시 와도 한 번만 예약한다
    public void reserve(UUID reservationId, Map<Long, Integer> quantities) {
        requirePositive(quantities);

        long result = stockRedisRepository.reserve(reservationId, quantities, System.currentTimeMillis());
        if (result == -1) {
            log.info("이미 취소된 예약이라 Try 거부 reservationId={}", reservationId);
            throw new BusinessException(ErrorCode.RESERVATION_CANCELLED);
        }
        if (result > 0) {
            Long productId = List.copyOf(quantities.keySet()).get((int) result - 1);
            log.info("Redis 재고 부족으로 예약 실패 reservationId={}, productId={}, quantities={}",
                    reservationId, productId, quantities);
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
    }

    // TCC Cancel — 예약이 없으면 취소 표식만 남겨, 늦게 도착한 Try 를 거부하게 한다
    public void cancel(UUID reservationId) {
        long result = stockRedisRepository.cancel(reservationId, reservationProperties.recordTtl());
        if (result == 1) {
            log.info("재고 예약 취소 reservationId={}", reservationId);
        } else if (result == 0) {
            log.info("취소할 예약이 없어 취소 표식만 남김 reservationId={}", reservationId);
        } else {
            log.warn("이미 확정된 예약이라 취소하지 않음 reservationId={}", reservationId);
        }
    }

    // TCC Confirm — Stock DB 차감이 커밋된 뒤 호출한다. available 은 Try 때 이미 줄었으므로 reserved 만 줄인다
    public void confirm(Long orderId, UUID reservationId) {
        long result = stockRedisRepository.confirm(reservationId, reservationProperties.recordTtl());
        if (result == -1) {
            // 예약이 만료 취소된 뒤에 주문이 확정됐다. DB 는 차감됐으므로 대사 스케줄러가 Redis 를 DB 에 맞춘다
            log.error("예약이 없거나 취소된 뒤 주문이 확정됨 — 과대 판매 여부 확인 필요 orderId={}, reservationId={}",
                    orderId, reservationId);
        }
    }

    // 주문 생성 이벤트를 받아 Stock DB 재고를 차감한다 (Redis 는 Try 때 이미 예약함)
    // DB 차감 · 처리 기록 · 재고 차감 이벤트를 한 트랜잭션으로 저장한다
    @Transactional
    public void decreaseForOrder(Long orderId, Map<Long, Integer> quantities) {
        requirePositive(quantities);

        if (processedOrderRepository.existsByOrderId(orderId)) {
            log.info("이미 재고를 차감한 주문이라 건너뜀 orderId={}", orderId);
            return;
        }
        // 먼저 insert 해 둬서, 같은 주문이 동시에 들어오면 뒤의 요청이 unique 제약에서 막힌다
        processedOrderRepository.saveAndFlush(ProcessedOrder.of(orderId));

        // 여러 주문이 같은 상품들을 잠글 때 데드락이 나지 않도록 productId 순서로 잠근다
        List<StockDecreased.Item> decreased = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : new TreeMap<>(quantities).entrySet()) {
            Stock stock = stockRepository.findByProductIdForUpdate(entry.getKey())
                    .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));
            stock.decrease(entry.getValue());
            decreased.add(new StockDecreased.Item(stock.getProductId(), entry.getValue(), stock.getQuantity()));
        }

        outboxWriter.write(Topics.STOCK_EVENTS, String.valueOf(orderId), new StockDecreased(orderId, decreased));
    }

    private void requirePositive(Map<Long, Integer> quantities) {
        if (quantities.isEmpty() || quantities.values().stream().anyMatch(q -> q == null || q <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_STOCK_QUANTITY);
        }
    }

    public Long increaseAvailable(Long productId, int quantity) {
        try {
            return stockRedisRepository.increaseAvailable(productId, quantity);
        } catch (RuntimeException e) {
            log.warn("Redis 재고 증가 실패 productId={}, quantity={}", productId, quantity, e);
            return null;
        }
    }
}
