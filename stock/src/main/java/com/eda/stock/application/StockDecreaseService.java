package com.eda.stock.application;

import com.eda.common.message.order.OrderCreated;
import com.eda.common.message.stock.StockDecreased;
import com.eda.common.outbox.OutboxWriter;
import com.eda.stock.domain.Stock;
import com.eda.stock.domain.StockDecrease;
import com.eda.stock.domain.StockDecreaseLine;
import com.eda.stock.domain.StockDecreaseRepository;
import com.eda.stock.domain.StockRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockDecreaseService {

    private static final String TOPIC = "stock.events";

    private final StockRepository stockRepository;
    private final StockDecreaseRepository stockDecreaseRepository;
    private final OutboxWriter outboxWriter;

    // 행 락을 productId 순으로 먼저 잡고 중복을 확인한다. 같은 주문이 겹쳐 들어와도 뒤쪽은 앞쪽 커밋 뒤에 중복을 본다.
    @Transactional
    public void decrease(UUID messageId, OrderCreated order) {
        List<OrderCreated.Item> items = order.items().stream()
                .sorted(Comparator.comparing(OrderCreated.Item::productId))
                .toList();

        List<Stock> stocks = new ArrayList<>();
        for (OrderCreated.Item item : items) {
            Optional<Stock> stock = stockRepository.findByProductIdForUpdate(item.productId());
            if (stock.isEmpty()) {
                log.error("재고 행이 없어 DB 재고를 차감하지 못했습니다 orderId={} orderKey={} productId={}",
                        order.orderId(), order.orderKey(), item.productId());
                return;
            }
            stocks.add(stock.get());
        }

        if (stockDecreaseRepository.existsByOrderId(order.orderId())) {
            log.info("이미 처리한 주문 생성 이벤트를 건너뜁니다 orderId={} orderKey={} messageId={}",
                    order.orderId(), order.orderKey(), messageId);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            if (stocks.get(i).getQuantity() < items.get(i).quantity()) {
                log.error("DB 재고가 부족해 차감하지 못했습니다 orderId={} orderKey={} productId={} 필요={} 남은={}",
                        order.orderId(), order.orderKey(), items.get(i).productId(),
                        items.get(i).quantity(), stocks.get(i).getQuantity());
                return;
            }
        }

        List<StockDecreaseLine> lines = new ArrayList<>();
        List<StockDecreased.Item> decreased = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Stock stock = stocks.get(i);
            OrderCreated.Item item = items.get(i);
            stock.decrease(item.quantity());
            lines.add(StockDecreaseLine.of(item.productId(), item.quantity()));
            decreased.add(new StockDecreased.Item(item.productId(), item.quantity(), stock.getQuantity()));
        }

        stockDecreaseRepository.save(StockDecrease.of(order.orderId(), order.orderKey(), lines));
        outboxWriter.write(TOPIC, String.valueOf(order.orderId()), new StockDecreased(order.orderId(), decreased));
    }
}
