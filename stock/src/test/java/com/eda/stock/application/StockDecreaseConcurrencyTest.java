package com.eda.stock.application;

import static org.assertj.core.api.Assertions.*;

import com.eda.common.message.order.OrderCreated;
import com.eda.common.outbox.OutboxRepository;
import com.eda.stock.domain.StockDecreaseRepository;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class StockDecreaseConcurrencyTest extends IntegrationTest {

    @Autowired
    private StockDecreaseService stockDecreaseService;

    @Autowired
    private StockRegistrationService stockRegistrationService;

    @Autowired
    private StockIncreaseService stockIncreaseService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockDecreaseRepository stockDecreaseRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private StringRedisTemplate redis;

    @AfterEach
    void tearDown() {
        outboxRepository.deleteAll();
        stockDecreaseRepository.deleteAll();
        stockRepository.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("같은 주문을 두 스레드가 동시에 처리해도 DB 재고는 한 번만 차감한다")
    void sameOrderConcurrently() throws Exception {
        stock(1L, 10);
        stock(2L, 10);
        OrderCreated order = new OrderCreated(1L, UUID.randomUUID().toString(),
                List.of(new OrderCreated.Item(1L, 2), new OrderCreated.Item(2L, 3)));

        runConcurrently(List.of(order, order));

        assertThat(quantity(1L)).isEqualTo(8);
        assertThat(quantity(2L)).isEqualTo(7);
        assertThat(stockDecreaseRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.findAll())
                .filteredOn(message -> message.getMessageType().equals("StockDecreased"))
                .hasSize(1);
    }

    @Test
    @DisplayName("상품 순서가 반대인 두 주문을 동시에 처리해도 교착 없이 둘 다 차감한다")
    void oppositeProductOrder() throws Exception {
        stock(1L, 10);
        stock(2L, 10);

        runConcurrently(List.of(
                new OrderCreated(1L, UUID.randomUUID().toString(),
                        List.of(new OrderCreated.Item(1L, 1), new OrderCreated.Item(2L, 1))),
                new OrderCreated(2L, UUID.randomUUID().toString(),
                        List.of(new OrderCreated.Item(2L, 1), new OrderCreated.Item(1L, 1)))));

        assertThat(quantity(1L)).isEqualTo(8);
        assertThat(quantity(2L)).isEqualTo(8);
        assertThat(stockDecreaseRepository.count()).isEqualTo(2);
    }

    private void runConcurrently(List<OrderCreated> orders) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(orders.size())) {
            for (OrderCreated order : orders) {
                results.add(executor.submit(() -> {
                    start.await();
                    stockDecreaseService.decrease(UUID.randomUUID(), order);
                    return null;
                }));
            }
            start.countDown();
        }
        for (Future<?> result : results) {
            result.get();
        }
    }

    private void stock(Long productId, int quantity) {
        stockRegistrationService.registerIfAbsent(productId);
        stockRegistrationService.initializeAvailable(productId);
        stockIncreaseService.increase(productId, quantity);
    }

    private int quantity(Long productId) {
        return stockRepository.findByProductId(productId).orElseThrow().getQuantity();
    }
}
