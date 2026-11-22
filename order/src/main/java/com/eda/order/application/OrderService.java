package com.eda.order.application;

import com.eda.common.exception.BusinessException;
import com.eda.common.exception.ErrorCode;
import com.eda.common.message.order.OrderCreated;
import com.eda.common.outbox.OutboxWriter;
import com.eda.order.domain.Order;
import com.eda.order.domain.OrderRepository;
import com.eda.order.domain.ProductSnapshot;
import com.eda.order.domain.ProductSnapshotRepository;
import com.eda.order.stock.StockCallException;
import com.eda.order.stock.StockClient;
import com.eda.order.stock.StockRestorer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String TOPIC = "order.events";

    private final ProductSnapshotRepository productSnapshotRepository;
    private final OrderRepository orderRepository;
    private final OutboxWriter outboxWriter;
    private final StockClient stockClient;
    private final StockRestorer stockRestorer;
    private final TransactionTemplate transactionTemplate;

    public record Item(Long productId, int quantity) {
    }

    // stock 호출은 트랜잭션 밖에서 한다. 응답을 기다리는 동안 DB 커넥션을 잡지 않기 위해서다.
    public Order create(Long memberId, List<Item> items) {
        Map<Long, ProductSnapshot> snapshots = productSnapshotRepository
                .findAllByProductIdIn(items.stream().map(Item::productId).toList()).stream()
                .collect(Collectors.toMap(ProductSnapshot::getProductId, Function.identity()));
        if (snapshots.size() != items.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        String orderKey = UUID.randomUUID().toString();
        Order order = Order.create(memberId, orderKey, items.stream()
                .map(item -> snapshots.get(item.productId()).toLineItem(item.quantity()))
                .toList());
        List<StockClient.Item> stockItems = items.stream()
                .map(item -> new StockClient.Item(item.productId(), item.quantity()))
                .toList();

        try {
            stockClient.decrease(orderKey, stockItems);
        } catch (StockCallException e) {
            if (e.mayHaveDecreased()) {
                restoreLater(orderKey, stockItems);
            }
            throw e;
        }

        try {
            return transactionTemplate.execute(status -> save(order));
        } catch (RuntimeException e) {
            restoreLater(orderKey, stockItems);
            throw e;
        }
    }

    private void restoreLater(String orderKey, List<StockClient.Item> items) {
        try {
            stockRestorer.restore(orderKey, items);
        } catch (TaskRejectedException e) {
            log.error("재고 복원 대기열이 가득 차 재고 복원을 끝내 실패했습니다 orderKey={} items={}", orderKey, items, e);
        }
    }

    private Order save(Order order) {
        Order saved = orderRepository.save(order);
        outboxWriter.write(TOPIC, String.valueOf(saved.getId()), new OrderCreated(saved.getId(), saved.getOrderKey(),
                saved.getOrderLines().stream()
                        .map(line -> new OrderCreated.Item(line.getProductId(), line.getQuantity()))
                        .toList()));
        return saved;
    }
}
