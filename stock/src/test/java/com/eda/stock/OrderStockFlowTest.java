package com.eda.stock;

import static com.eda.stock.support.OrderEvents.orderCreated;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eda.common.message.order.OrderCreated;
import com.eda.common.outbox.OutboxRepository;
import com.eda.stock.application.StockIncreaseService;
import com.eda.stock.application.StockRegistrationService;
import com.eda.stock.domain.StockDecreaseRepository;
import com.eda.stock.domain.StockRepository;
import com.eda.stock.support.IntegrationTest;
import java.time.Duration;
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
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// Issue #24 완료 조건: 재고 10개에 1개씩 동시 100건 → 10건만 성공, Redis 가 먼저 0, 이어서 Stock DB 도 0.
// 테스트 Kafka 는 파티션 1개라 DB 차감은 순서대로 소비된다. DB 쪽 동시성은 StockDecreaseConcurrencyTest 가 본다.
class OrderStockFlowTest extends IntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

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
    @DisplayName("재고 10개에 1개씩 동시에 100건 주문하면 10건만 성공하고, Redis 가 먼저 0 이 된 뒤 Stock DB 도 0 이 된다")
    void hundredConcurrentOrders() throws Exception {
        stockRegistrationService.registerIfAbsent(1L);
        stockRegistrationService.initializeAvailable(1L);
        stockIncreaseService.increase(1L, 10);
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        int requests = 100;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Decrease>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(() -> {
                    String orderKey = UUID.randomUUID().toString();
                    start.await();
                    int status = mockMvc.perform(post("/internal/stocks/decrease")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{ \"orderKey\": \"%s\", \"items\": [{ \"productId\": 1, \"quantity\": 1 }] }"
                                            .formatted(orderKey)))
                            .andReturn().getResponse().getStatus();
                    return new Decrease(orderKey, status);
                }));
            }
            start.countDown();
        }
        List<Decrease> results = new ArrayList<>();
        for (Future<Decrease> future : futures) {
            results.add(future.get());
        }

        List<Decrease> succeeded = results.stream().filter(result -> result.status() == 200).toList();
        assertThat(succeeded).hasSize(10);
        assertThat(results).filteredOn(result -> result.status() == 409).hasSize(90);
        assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("0");
        assertThat(quantity()).isEqualTo(10);

        long orderId = 1;
        for (Decrease decrease : succeeded) {
            kafkaTemplate.send(orderCreated(UUID.randomUUID(),
                    new OrderCreated(orderId++, decrease.orderKey(), List.of(new OrderCreated.Item(1L, 1)))));
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(quantity()).isZero());
        assertThat(stockDecreaseRepository.count()).isEqualTo(10);
        assertThat(redis.opsForValue().get("stock:available:1")).isEqualTo("0");
    }

    private int quantity() {
        return stockRepository.findByProductId(1L).orElseThrow().getQuantity();
    }

    private record Decrease(String orderKey, int status) {
    }
}
